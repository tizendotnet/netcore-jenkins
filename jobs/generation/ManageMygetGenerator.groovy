
folder('./manage_myget') {}

def projectName = "manage_tizen_nupkgs"
def newJob = job('./manage_myget/' + projectName) {
  scm {
    git {
      remote {
        github('tizendotnet/netcore-jenkins')
      }
      branch("*/master")
    }
  }

  wrappers {
    timestamps()
    // Add a pre-build wipe-out
    preBuildCleanup()
  }

  parameters {
    stringParam('PUSH_METADATA', '')
  }

  steps {
    buildDescription('',"[INFO]")
    def cmd = "#!/bin/bash"
    cmd += "\n meta_file=\"metadata.json\""
    cmd += "\n echo \"\${PUSH_METADATA}\" > \${meta_file}"
    cmd += "\n python  python/delete_old_nupkgs.py --metafile=\${meta_file} --key='${NUGET_API_KEY}'"
    shell("${cmd}")
  }
}

projectName = "start_webhook"
newJob = job('./manage_myget/' + projectName) {
  scm {
    git {
      remote {
        github('tizendotnet/netcore-jenkins')
      }
      branch("*/master")
    }
  }

  wrappers {
    timestamps()
    // Add a pre-build wipe-out
    preBuildCleanup()
  }

  parameters {
    stringParam('BUILD_ID', 'dontKillMe')
  }

  steps {
    buildDescription('',"[INFO]")
    def cmd = "#!/bin/bash"
    cmd += "\n "
    cmd += "\n ### precondition"
    cmd += "\n # curl -sL https://deb.nodesource.com/setup_6.x | sudo -E bash -"
    cmd += "\n # sudo apt-get update"
    cmd += "\n # sudo apt-get install nodejs"
    cmd += "\n # sudo npm install -g express"
    cmd += "\n # sudo npm install -g requrie"
    cmd += "\n # sudo npm install -g body-parser"
    cmd += "\n # sudo npm install -g jenkins"
    cmd += "\n # sudo npm install -g forever"
    cmd += "\n "
    cmd += "\n cd nodejs"
    cmd += "\n npm link express"
    cmd += "\n npm link request"
    cmd += "\n npm link body-parser"
    cmd += "\n npm link jenkins"
    cmd += "\n forever stop dotnet-webhook.js"
    cmd += "\n forever start --minUptime 5000 --spinSleepTime 2000 -o dotnet-webhook.log dotnet-webhook.js ${JENKINS_KEY}"
    cmd += "\n forever list" 
    cmd += "\n "
    cmd += "\n "
    shell("${cmd}")
  }
}
