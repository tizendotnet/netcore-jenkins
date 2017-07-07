// Import the utility functionality

import jobs.generation.Utilities

def projectLoop = [ 'coreclr', 'corefx', 'core-setup' ]
def config = 'Release'
def targetArch = 'armel'

folder('generator') {}

job('generator/official_poll') {
  logRotator {
    daysToKeep(7)
  }

  scm {
    git {
      remote {
        github('jyoungyun/netcore-jenkins')
      }
      branch('*/master')
    }
  }

  parameters {
    stringParam('coreclr_version', '2.0.0')
    stringParam('coreclr_minor_version', 'preview2-25407-01')
    stringParam('corefx_version', '2.0.0')
    stringParam('corefx_minor_version', 'preview2-25405-01')
    stringParam('core_setup_version', '2.0.0')
    stringParam('core_setup_minor_version', 'preview2-25407-01')
    stringParam('patch_version', 'v2.0.0-preview2-tizen')
  }

  projectLoop.each { projectName ->

    def paramMap = [ 'coreclr':
              [ 'version': "\${coreclr_version}",
                'minor_version': "\${coreclr_minor_version}" ],
                'corefx':
              [ 'version': "\${corefx_version}",
                'minor_version': "\${corefx_minor_version}" ],
                'core-setup':
              [ 'version': "\${core_setup_version}",
                'minor_version': "\${core_setup_minor_version}" ]
             ]

    def version = paramMap."${projectName}"['version']
    def minor_version = paramMap."${projectName}"['minor_version']

    def fullJobName = "${projectName}_${targetArch}"

    def propDir = ""
    if ( minor_version == null ) {
        propDir = "${projectName}/${version}"
    } else {
        propDir = "${projectName}/${version}/${minor_version}"
    }

    def patch = "${projectName}-\${patch_version}.patch"

    steps {
      conditionalSteps {
        condition {
          shell("jobs/scripts/get_version.sh ${projectName} ${version} ${minor_version} ${patch}")
        }

        steps {
          environmentVariables {
            propertiesFile("${propDir}/build_dev.properties")
          }
          buildDescription('',"[INFO] Trigger projects ${fullJobName} ${version} ${minor_version}")
          downstreamParameterized {
            trigger('official-release/' + fullJobName) {
              parameters {
                propertiesFile("${propDir}/build_dev.properties")
              }
            }
          }
        }
      }
    }
  }
}

folder('official-release') {}

projectLoop.each { projectName ->

  def netcoreDir = "netcore-jenkins"
  def projectDir = "dotnet_${projectName}"
  def fullJobName = "${projectName}_${targetArch}"
  def newJob = job('official-release/' + fullJobName) {
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
          github('jyoungyun/netcore-jenkins')
        }
        branch("*/master")

        extensions {
          relativeTargetDirectory("${netcoreDir}")
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
      stringParam('minor_version', '')
      stringParam('sha1', '')
      stringParam('buildid', '')
      stringParam('patch', '')
      stringParam('config', config)
      stringParam('targetArch', targetArch)
    }
    steps {
      buildDescription('',"[INFO] \${version} \${minor_version}")
    }
  }

  // Set retention policy
  Utilities.addRetentionPolicy(newJob)
  // Apply patch
  def patch = "${netcoreDir}/patches/\${patch}"
  Utilities.applyPatch(newJob, patch, projectDir)
  // Set a build steps
  Utilities.addBuildSteps(newJob, projectName, projectDir)
  // Upload packages to the predefined myget server
  def nugetMap = [ 'feed':"${NUGET_FEED}", 'sfeed':"${NUGET_SFEED}", 'key':"${NUGET_API_KEY}" ]
  Utilities.addUploadSteps(newJob, nugetMap, projectName, projectDir)
  // Archive results
  Utilities.addArchival(newJob, projectName, projectDir)

}

