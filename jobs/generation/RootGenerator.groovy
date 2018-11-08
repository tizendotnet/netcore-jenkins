// Import the utility functionality

import jobs.generation.Utilities

def projectLoop = [ 'coreclr', 'corefx', 'core-setup' ]
def branchLoop = [ 'coreclr' :
                  [ 'master',
                    'release/2.0.0',
                    'release/2.1' ],
                   'corefx' :
                  [ 'master',
                    'release/2.0.0',
                    'release/2.1' ],
                   'core-setup' :
                  [ 'master',
                    'release/2.0.0',
                    'release/2.1' ]
                  ]
def config = 'Release'
def targetArch = 'armel'

folder('generator') {}

job('generator/poll') {
  logRotator {
    daysToKeep(7)
  }

  multiscm {
    git {
      remote {
        github('dotnet/versions')
      }
      branch('*/master')

      extensions {
        relativeTargetDirectory('dotnet-versions')
      }
    }
    git {
      remote {
        github('tizendotnet/netcore-jenkins')
      }
      branch('*/master')

      extensions {
        relativeTargetDirectory('netcore-jenkins')
      }
    }
  }

  triggers {
    cron('H/10 * * * *')
  }

  projectLoop.each { projectName ->
    (branchLoop[projectName]).each { branchName ->

      def fullJobName = Utilities.getProjectName(projectName, branchName, targetArch, config)

      steps {
        conditionalSteps {
          condition {
            shell("netcore-jenkins/jobs/scripts/check_version.sh ${projectName} ${branchName}")
          }

          steps {
            environmentVariables {
              propertiesFile("${projectName}/${branchName}/build_dev.properties")
            }
            buildDescription('',"[INFO] Trigger projects ${fullJobName} \${version}(\${buildid})")
            downstreamParameterized {
              trigger('release/' + fullJobName) {
                parameters {
                  propertiesFile("${projectName}/${branchName}/build_dev.properties")
                }
              }
            }
          }
        }
      }

    }
  }

}

folder('release') {}

projectLoop.each { projectName ->
  (branchLoop[projectName]).each { branchName ->

    def projectDir = "dotnet_${projectName}"
    def fullJobName = Utilities.getProjectName(projectName, branchName, targetArch, config)
    def newJob = job('release/' + fullJobName) {
      multiscm {
        git {
          remote {
            github("dotnet/${projectName}")
          }
          branch('${sha1}')

          extensions {
            relativeTargetDirectory("${projectDir}")
          }
        }
        git {
          remote {
            github('tizendotnet/netcore-jenkins')
          }
          branch("*/master")

          extensions {
            relativeTargetDirectory('netcore-jenkins')
          }
        }
      }

      wrappers {
        timestamps()
        // Add a pre-build wipe-out
        preBuildCleanup()
      }
      parameters {
        stringParam('version', '')
        stringParam('sha1', '')
        stringParam('buildid', '')
        stringParam('config', config)
        stringParam('targetArch', targetArch)
      }
      steps {
        buildDescription('',"[INFO] \${version}(\${buildid})")
      }
    }

    // Set retention policy
    Utilities.addRetentionPolicy(newJob)
    // Set a build steps
    Utilities.addBuildSteps(newJob, projectName, branchName, projectDir)
    // Upload packages to the predefined myget server
    def nugetMap = [ 'feed':"${NUGET_FEED}", 'sfeed':"${NUGET_SFEED}", 'key':"${NUGET_API_KEY}" ]
    Utilities.addUploadSteps(newJob, nugetMap, projectName, projectDir)
    // Archive results
    Utilities.addArchival(newJob, projectName, projectDir)
    // Set email notifications for unstable builds
    Utilities.setEmailNotification(newJob, "${EMAIL}")
  }
}
