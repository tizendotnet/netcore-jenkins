
package jobs.generation

class Utilities {

  /**
   * Get the folder name for a job.
   *
   * @param project Project name (e.g. dotnet/coreclr)
   * @return Folder name for the project. Typically project name with / turned to _
   */
  def static getFolderName(String project) {
    return project.replace('/', '_')
  }

  /**
   * Get the project name for a job.
   *
   * @param project Project name (e.g. coreclr)
   * @param branch Branch name (e.g. master)
   * @param targetArch Target architecture name (e.g. armel)
   * @param config Build configuration (e.g. release)
   * @return Project name for a job.
   */
  def static getProjectName(String project, String branch, String targetArch, String config) {
    return getFolderName("${project}_${branch}_${targetArch}_${config}")
  }

  def private static String getDockerImage(String branch) {
    switch (branch) {
      case 'release/2.0.0':
        return 'hqueue/dotnetcore:ubuntu1404_cross_prereqs_v4-tizen_rootfs'
      case 'release/2.1':
      case 'release/2.2':
        return 'tizendotnet/dotnet-buildtools-prereqs:ubuntu-16.04-cross-e435274-20180426002255-tizen-rootfs-5.0m1'
      case 'release/3.0':
      case 'master':
        return 'tizendotnet/dotnet-buildtools-prereqs:ubuntu-16.04-cross-10fcdcf-20190208200917-tizen-rootfs-5.0m2'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  def private static String getTizenVersion(String branch) {
    switch (branch) {
      case 'release/2.0.0':
        return 'tizen.4.0.0'
      case 'release/2.1':
      case 'release/2.2':
      case 'release/3.0':
      case 'master':
        return 'tizen.5.0.0'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  /**
   * Get the docker command.
   *
   * @param projectDir (optional) Project directory to work
   * @return Docker command
   */
  def static getDockerCommand(String branch, String projectDir = "") {
    def workingDirectory = "/opt/code"
    def environment = "-e ROOTFS_DIR=/crossrootfs/\${targetArch}.tizen.build"
    def command = "docker run --rm -v \${WORKSPACE}${projectDir}:${workingDirectory} -w=${workingDirectory} ${environment} ${getDockerImage(branch)}"
    return command
  }

  /**
   * Add a build steps.
   *
   * @param job Job to add build steps
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addBuildSteps(def job, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = "/" + projectDir
    }

    def dockerCommand = getDockerCommand(branch, projectDir)

    job.with {
      steps {

        // see https://github.com/dotnet/corefx/pull/30825 for details of versioning

        conditionalSteps {
          condition {
            and {
              not {
                stringsMatch("\${version}", "stable", false)
              }
            }
            {
              not {
                stringsMatch("\${version}", "servicing", false)
              }
            }
            {
              not {
                stringsMatch("\${version}", "rtm", false)
              }
            }
          }

          steps {
            environmentVariables {
              env("buildIdOpts", "/p:OfficialBuildId=\${buildid}")
            }
          }
        }

        conditionalSteps {
          condition {
            stringsMatch("\${version}", "stable", false)
          }

          steps {
            environmentVariables {
              env("stableOpts", "/p:StabilizePackageVersion=true")
              env("packageOpts", "/p:PackageVersionStamp=")
            }
          }
        }

        if (project == 'corefx') {
          conditionalSteps {
            condition {
              shell("echo \${version} | grep -q servicing")
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=servicing")
              }
            }
          }

          conditionalSteps {
            condition {
              shell("echo \${version} | grep -q rtm")
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=rtm")
              }
            }
          }
        } else {
          conditionalSteps {
            condition {
              or {
                stringsMatch("\${version}", "servicing", false)
              }
              {
                stringsMatch("\${version}", "rtm", false)
              }
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=\${buildid}")
              }
           }
          }
        }

        def passCI = ''
        if (project == 'corefx' && (branch == 'master' || branch == 'release/3.0')) {
          // Master uses Arcade for build and requires --ci option to be passed
          passCI = '--ci'
        }
        else if (project == 'coreclr' && (branch == 'master' || branch == 'release/3.0')) {
          passCI = '/p:ContinuousIntegrationBuild=true'
        }

        def authorsOpts = '/p:Authors=Tizen'

        if (project == 'coreclr') {
          shell("${dockerCommand} ./build.sh cross \${config} \${targetArch} cmakeargs -DFEATURE_GDBJIT=TRUE cmakeargs -DFEATURE_PREJIT=TRUE cmakeargs -DFEATURE_NGEN_RELOCS_OPTIMIZATIONS=TRUE stripSymbols -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} ${authorsOpts} ${passCI}")
        } else if (project == 'corefx') {
          if (branch == 'master' || branch == 'release/3.0') {
            // Build command for CoreFX has changed: see https://github.com/dotnet/corefx/pull/32798/files and https://github.com/dotnet/corefx/commit/66392f577c7852092f668876822b6385bcafbd44

            shell("${dockerCommand} ./build.sh --configuration \${config} /p:ArchGroup=\${targetArch} /p:RuntimeOS=${getTizenVersion(branch)} /p:PortableBuild=false /p:EnableNgenOptimization=false ${passCI} \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
          } else {
            shell("${dockerCommand} ./build-managed.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
            shell("${dockerCommand} ./build-native.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
            shell("${dockerCommand} ./build-packages.sh -\${config} -ArchGroup=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- ${authorsOpts}")
          }
        } else if (project == 'core-setup') {
          shell("${dockerCommand} ./build.sh -ConfigurationGroup=\${config} -TargetArchitecture=\${targetArch} -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts} /p:OutputRid=${getTizenVersion(branch)}-\${targetArch}")
        }
        // Change ownership to UID of the projectDir
        // Building with docker, it will be created as root with the file it downloaded
        // or generated during the build process. In this case, when jenkins remove workspace,
        // it can not access root permission files, so it can not be removed normally.
        // This issue causes the disk size to become insufficient.
        shell("${dockerCommand} chown \$( id -u \${USER} ):\$( id -u \${USER} ) . -R")
      }
    }
  }

  /**
   * Archives data for a job.
   *
   * @param job Job to modify
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addArchival(def job, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    job.with {
      publishers {
        archiveArtifacts {
          if (project == 'coreclr') {
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/pkg/*.nupkg')
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/symbolpkg/*.nupkg')
          } else if (project == 'corefx') {
            if (branch == "master" || branch == "release/3.0") {
              // On latest master bin/ folder is moved to artifacts/
              pattern(projectDir + 'artifacts/packages/\${config}/*.nupkg')
            } else {
              pattern(projectDir + 'bin/packages/\${config}/*.nupkg')
            }
          } else if (project == 'core-setup') {
            if (branch == 'master' || branch == 'release/3.0') {
              pattern(projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.nupkg")
              pattern(projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.tar.gz")
            } else {
              pattern(projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.nupkg")
              pattern(projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.tar.gz")
            }
          }
          onlyIfSuccessful()
        }
      }
    }
  }

  /**
   * Get the nuget command.
   *
   * @param nugetMap Nuget configuration settings
   * @param path Directory path for uploading packages
   * @return Nuget command
   */
  def static getNugetCommand(def nugetMap, String path) {
    return "netcore-jenkins/jobs/scripts/upload_nupkg.sh netcore-jenkins/dotnet-dev " + path + " " + nugetMap['feed'] + " " + nugetMap['sfeed'] + " " + nugetMap['key']
  }

  /**
   * Upload packages for a job.
   *
   * @param job Job to modify
   * @param nugetMap Nuget configuration settings
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addUploadSteps(def job, def nugetMap, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    def nugetCommand = ""
    if (project == 'coreclr') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget')
    } else if (project == 'corefx') {
      if (branch == "master" || branch == "release/3.0") {
        // On latest master bin/ folder is moved to artifacts/
        nugetCommand = getNugetCommand(nugetMap, projectDir + 'artifacts/packages/\${config}')
      } else {
        nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/packages/\${config}')
      }
    } else if (project == 'core-setup') {
      if (branch == 'master' || branch == 'release/3.0') {
        nugetCommand = getNugetCommand(nugetMap, projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages")
      } else {
        nugetCommand = getNugetCommand(nugetMap, projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages")
      }
    }

    job.with {
      steps {
        shell("set +x && ${nugetCommand}")
      }
    }
  }

  /**
   * Add a retention policy for artifacts.
   *
   * @param job Job to modify
   */
  def static addRetentionPolicy(def job) {
    job.with {
      logRotator {
        artifactDaysToKeep(10)
        daysToKeep(21)
        artifactNumToKeep(50)
        numToKeep(100)
      }
    }
  }

  /**
   * Set email notifications for unstable builds.
   *
   * @param job Job to modify
   * @param recipients Recipients to receive
   */
  def static setEmailNotification(def job, String recipients) {
    job.with {
      publishers {
        mailer(recipients, false, false)
      }
    }
  }

  /**
   * Apply patch.
   *
   * @param job Job to apply patch
   * @param patch Patch to apply
   * @param projectDir (optional) Project directory to build
   */
  def static applyPatch(def job, String patch, String projectDir = "") {
    job.with {
      steps {
        shell("if [ -f ${patch} ]; then git -C \${WORKSPACE}/${projectDir} apply \${WORKSPACE}/${patch}; fi")
      }
    }
  }
}
