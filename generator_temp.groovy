folder('dotnet') {}

def generatorJob = job('dotnet/generator') {
  logRotator {
    daysToKeep(7)
  }
  triggers {
    cron('H H/6 * * *')
  }
  steps {
    conditionalSteps {
      condition {
        fileExists('env.dotnet_build_id', BaseDir.WORKSPACE)
      }
      steps {
        environmentVariables {
          propertiesFile('env.dotnet_build_id')
        }
      }
    }
    shell('''\
date=${DOTNET_BUILD_ID%%-*}
no=$( echo ${DOTNET_BUILD_ID##*-} | sed 's/^0*//' )
today=$(TZ=UTC date +%Y%m%d)
if [ "${date}" = "${today}" ]; then
  no=$((no + 1))
else
  no=1
fi
echo DOTNET_BUILD_ID="${today}-$( printf %02d ${no} )" > env.dotnet_build_id
''')
  }
  publishers {
    downstreamParameterized {
      trigger('dotnet/coreclr, dotnet/corefx, dotnet/core-setup') {
        condition('SUCCESS')
        parameters {
          propertiesFile('env.dotnet_build_id')
          predefinedProp('TARGET_ARCH', 'armel')
          predefinedProp('CONFIG', 'Release')
        }
      }
    }
  }
}

class Utilities {
  /**
   * Get the Docker command
   */
  def static getDockerCommand() {
    def repository = "hqueue/dotnetcore"
    def container = "ubuntu1404_cross_prereqs_v4-tizen_rootfs"
    def workingDirectory = "/opt/code"
    def environment = "-e ROOTFS_DIR=/crossrootfs/\${TARGET_ARCH}.tizen.build"
    def command = "docker run --rm -v \${WORKSPACE}:${workingDirectory} -w=${workingDirectory} ${environment} ${repository}:${container}"
    return command
  }    
}

def static setCLRJob(def job) {
  def dockerCommand = Utilities.getDockerCommand()
  job.with {
    steps {
      shell("${dockerCommand} ./build.sh cross \${TARGET_ARCH} \${CONFIG} cmakeargs -DFEATURE_GDBJIT=TRUE stripSymbols -OfficialBuildId=\${DOTNET_BUILD_ID} -- /p:Authors=Tizen")
      shell('mkdir output && find ./bin/Product/Linux.${TARGET_ARCH}.${CONFIG}/.nuget/ -iname "*.nupkg" -exec cp {} output \\;')
    }
    publishers {
      archiveArtifacts {
        pattern('output/*.nupkg')
        onlyIfSuccessful()
      }
    }
  }
}

def static setFXJob(def job) {
  def dockerCommand = Utilities.getDockerCommand()
  job.with {
    steps {
      shell("${dockerCommand} ./build.sh -\${CONFIG} -buildArch=\${TARGET_ARCH} -RuntimeOS=tizen.4.0.0 -OfficialBuildId=\${DOTNET_BUILD_ID} -- /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json")
      shell("${dockerCommand} ./build-packages.sh -\${CONFIG} -ArchGroup=\${TARGET_ARCH} -RuntimeOS=tizen.4.0.0 -- /p:Authors=Tizen")
      shell('mkdir output && find ./bin/packages/${CONFIG} -iname "*.nupkg" -exec cp {} output \\;')
    }
    publishers {
      archiveArtifacts {
        pattern('output/*.nupkg')
        onlyIfSuccessful()
      }
    }
  }
}

def static setSetupJob(def job) {
  def dockerCommand = Utilities.getDockerCommand()
  job.with {
    steps {
      shell("${dockerCommand} ./build.sh -ConfigurationGroup=\${CONFIG} -TargetArchitecture=\${TARGET_ARCH} -DistroRid=tizen.4.0.0-\${TARGET_ARCH} -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true -OfficialBuildId=\${DOTNET_BUILD_ID} -- /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json /p:Authors=Tizen")
      shell('mkdir output && find ./Bin/tizen.4.0.0-${TARGET_ARCH}.${CONFIG}/packages \\( -iname "*.nupkg" -or -iname "*.tar.gz" \\) -exec cp {} output \\;')
    }
    publishers {
      archiveArtifacts {
        pattern('output/*.nupkg')
        pattern('output/*.tar.gz')
        onlyIfSuccessful()
      }
    }
  }
}

['coreclr', 'corefx', 'core-setup'].each { name ->

  def newJob = job("dotnet/${name}") {
    scm {
      git {
        remote {
          github("dotnet/${name}")
        }
        branch('master')
      }
    }
    wrappers {
      timestamps()
      // Add a pre-build wipe-out
      preBuildCleanup()
    }
    parameters {
      stringParam('DOTNET_BUILD_ID', '')
      stringParam('TARGET_ARCH', '')
      stringParam('CONFIG', '')
    }
  }

  if (name.equals('coreclr')) {
    setCLRJob(newJob)
  } else if (name.equals('corefx')) {
    setFXJob(newJob)
  } else if (name.equals('core-setup')) {
    setSetupJob(newJob)
  }

  newJob.with {
    publishers {
      downstream("dotnet/upload-${name}")
    }
  }
}

def upload_script="""
CLI_NAME=\"dotnet-dev-ubuntu-x64.latest.tar.gz\"
CLI_URL=\"https://dotnetcli.blob.core.windows.net/dotnet/Sdk/master/\"\${CLI_NAME}
CLI_DIR=\"dotnet-dev\"

NUGET_URL=\"https://tizen.myget.org/F/dotnet-core/api/v3/index.json\"
NUGET_SYMBOLS_URL=\"https://tizen.myget.org/F/dotnet-core/symbols/api/v2/package\"

wget \${CLI_URL}
if [[ \$? != 0 ]]; then
  echo \"ERROR: Fail to download dotnet cli\"
  exit 1
fi
mkdir -p \${CLI_DIR}
tar xzf \${CLI_NAME} -C \${CLI_DIR}

for NUPKG in \$( find repo -iname \"*.nupkg\" -not -iname \"*symbols*\" ); do
  \${CLI_DIR}/dotnet nuget push \${NUPKG} -s \${NUGET_URL} -k \${NUGET_API_KEY} || true
done
for SYMBOLS in \$( find repo -iname \"*.symbols.nupkg\" ); do
  \${CLI_DIR}/dotnet nuget push \${NUPKG} -s \${NUGET_SYMBOLS_URL} -k \${NUGET_API_KEY} || true
done
"""

['coreclr', 'corefx', 'core-setup'].each { name ->
  def uploadJob = job("dotnet/upload-${name}") {
    wrappers {
      timestamps()
      // Add a pre-build wipe-out
      preBuildCleanup()
    }
    parameters {
      stringParam('NUGET_API_KEY', "${NUGET_API_KEY}")
    }
    steps {
      copyArtifacts("dotnet/${name}") {
        includePatterns('output/*.nupkg', 'output/*.tar.gz')
        targetDirectory('repo')
        flatten()
      }
      shell(upload_script)
    }
  }
}
