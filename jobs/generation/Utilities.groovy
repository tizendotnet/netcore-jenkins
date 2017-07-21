
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

  /**
   * Get the docker command.
   *
   * @param projectDir (optional) Project directory to work
   * @return Docker command
   */
  def static getDockerCommand(String projectDir = "") {
    def repository = "hqueue/dotnetcore"
    def container = "ubuntu1404_cross_prereqs_v4-tizen_rootfs"
    def workingDirectory = "/opt/code"
    def environment = "-e ROOTFS_DIR=/crossrootfs/\${targetArch}.tizen.build"
    def command = "docker run --rm -v \${WORKSPACE}${projectDir}:${workingDirectory} -w=${workingDirectory} ${environment} ${repository}:${container}"
    return command
  }

  /**
   * Add a build steps.
   *
   * @param job Job to add build steps
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addBuildSteps(def job, String project, String projectDir = "") {
    if (projectDir != "") {
        projectDir = "/" + projectDir
    }

    def dockerCommand = getDockerCommand(projectDir)

    job.with {
      steps {
        conditionalSteps {
          condition {
            not {
              stringsMatch("\${buildid}", "stable", false)
            }
          }

          steps {
            environmentVariables {
              env("buildIdOpts", "-OfficialBuildId=\${buildid}")
            }
          }
        }

        def authorsOpts = '/p:Authors=Tizen'

        if (project == 'coreclr') {
          shell("${dockerCommand} ./build.sh cross \${config} \${targetArch} cmakeargs -DFEATURE_GDBJIT=TRUE stripSymbols -PortableBuild=false \${buildIdOpts} -- ${authorsOpts}")
        } else if (project == 'corefx') {
          shell("${dockerCommand} ./build.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=tizen.4.0.0 -PortableBuild=false \${buildIdOpts} -- /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
          shell("${dockerCommand} ./build-packages.sh -\${config} -ArchGroup=\${targetArch} -RuntimeOS=tizen.4.0.0 -PortableBuild=false -- ${authorsOpts}")
        } else if (project == 'core-setup') {
          shell("${dockerCommand} ./build.sh -ConfigurationGroup=\${config} -TargetArchitecture=\${targetArch} -DistroRid=tizen.4.0.0-\${targetArch} -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true \${buildIdOpts} -- /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
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
  def static addArchival(def job, String project, String projectDir = "") {
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
            pattern(projectDir + 'bin/packages/\${config}/*.nupkg')
          } else if (project == 'core-setup') {
            pattern(projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages/*.nupkg')
            pattern(projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages/*.tar.gz')
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
  def static addUploadSteps(def job, def nugetMap, String project, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    def nugetCommand = ""
    if (project == 'coreclr') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget')
    } else if (project == 'corefx') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/packages/\${config}')
    } else if (project == 'core-setup') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages')
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
