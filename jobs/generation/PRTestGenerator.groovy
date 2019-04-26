// Import the utility functionality

import jobs.generation.Utilities

abstract class Project {
  static String ROOT_FOLDER = 'prjobs'

  String name
  List<String> branchList
  List<String> scenarioList
  List<String> configurationList
  List<String> osList = ['Tizen']
  List<String> archList = ['armel']

  abstract def setupJob(def newJob, String branch, String scenario, boolean isPR, String architecture, String configuration, String os)

  boolean shouldGenerateJob(String scenario, boolean isPR, String architecture, String configuration, String os) {
    return true
  }

  static String getProjectFolderName(String name) {
    return ROOT_FOLDER + '/' + name.replace('/', '_').replace('-', '_')
  }

  static String getJobFolderName(String name, String branch) {
    return getProjectFolderName(name) + '/' + branch.replace('/', '_')
  }

  static String getJobName(String configuration, String architecture, String os, String scenario) {
    String suffix = scenario != 'normal' ? "_${scenario}" : ''
    String baseName = ''
    switch (architecture) {
      case 'armel':
        baseName = architecture.toLowerCase() + '_cross_' + configuration.toLowerCase() + '_' + os.toLowerCase()
        break
      default:
        assert false : "Unknown architecture: '${architecture}'"
        break
    }

    return baseName + suffix
  }

  static String getCorefxBinPath(String branch) {
    switch (branch) {
      case 'release/2.0.0':
      case 'release/2.1':
      case 'release/2.2':
        return 'bin'
      case 'release/3.0':
      case 'master':
        return 'artifacts/bin'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  String getProjectFolderName() {
    return getProjectFolderName(name)
  }

  String getJobFolderName(String branch) {
    return getJobFolderName(name, branch)
  }

  def static getOSGroup(def os) {
    def osGroupMap = ['Tizen': 'Linux']
    def osGroup = osGroupMap.get(os, null)
    assert osGroup != null : "Could not find os group for ${os}"
    return osGroupMap[os]
  }

  def static addArchival(def job, String filesToArchive, String filesToExclude = '') {
    job.with {
      publishers {
        flexiblePublish {
          conditionalAction {
            condition {
              status('SUCCESS', 'SUCCESS')
            }

            publishers {
              archiveArtifacts {
                allowEmpty(false)
                pattern(filesToArchive)
                exclude(filesToExclude)

                // Always archive so that the flexible publishing
                // handles pass/fail
                onlyIfSuccessful(false)
              }
            }
          }
        }
      }
    }
  }
}

class ProjectCoreclr extends Project {
  def setupJob(def newJob, String branch, String scenario, boolean isPR, String architecture, String configuration, String os) {
    Utilities.standardJobSetup(newJob, name, true, "*/${branch}")

    addPRTriggers(newJob, branch, architecture, os, configuration, scenario)
    setJobTimeout(newJob, architecture, configuration, scenario)

    newJob.with {
      steps {
        copyArtifacts("${getJobFolderName('dotnet/corefx', branch)}/${getJobName(configuration != 'Checked' ? configuration : 'Release', architecture, os, scenario)}") {
          includePatterns("${getCorefxBinPath(branch)}/build.tar.gz")
          buildSelector {
            latestSuccessful(true)
          }
        }
      }
    }

    // Apply patch to build tests on Linux
    if (branch == 'release/2.0.0') {
      String patch = 'coreclr-v2.0.0-tests.patch'
      String newJobName = getJobName(configuration, architecture, os, scenario)
      String folderName = getJobFolderName(branch)
      String projectDir = Utilities.getFullJobName(name, newJobName, isPR, folderName)
      Utilities.getPatch(newJob, patch)
      Utilities.applyPatch(newJob, patch)
    }

    List<String> buildCommands = calculateBuildCommands(newJob, scenario, branch, architecture, configuration, os, configuration != 'Checked')

    newJob.with {
      steps {
        buildCommands.each { buildCommand ->
          shell(buildCommand)
        }
      }
    }
  }

  boolean shouldGenerateJob(String scenario, boolean isPR, String architecture, String configuration, String os) {
    // Generate only PR jobs
    return isPR
  }

  static String getDotnetPath(String branch)
  {
    switch (branch) {
      case 'release/2.0.0':
      case 'release/2.1':
      case 'release/2.2':
        return './Tools/dotnetcli/dotnet'
      case 'release/3.0':
      case 'master':
        return './.dotnet/dotnet'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  static List<String> calculateBuildCommands(def newJob, String scenario, String branch, String architecture,
                                             String configuration, String os, boolean isBuildOnly) {
    List<String> buildCommands = []
    String osGroup = getOSGroup(os)
    String lowerConfiguration = configuration.toLowerCase()

    // Calculate the build steps, archival, and xunit results
    switch (os) {
      case 'Tizen':
        switch (architecture) {
          case 'armel':
            String linuxCodeName = os.toLowerCase()

            assert scenario == 'normal' : "Unsupported scenario: '${scenario}'"

            String xUnitTestBinBase = "./bin/tests/Windows_NT.x64.${configuration}"

            if (!isBuildOnly) {
              def dockerCommand = Utilities.getDockerCommand(branch)

              // Build tests
              buildCommands += "${dockerCommand} ./init-tools.sh"

              if (branch == 'release/2.0.0') {
                buildCommands += "${dockerCommand} ${getDotnetPath(branch)} build ilasm.depproj /p:ProjectDir=/opt/code/ /p:ToolsDir=/opt/code/Tools/"
              }

              buildCommands += "${dockerCommand} ${getDotnetPath(branch)} restore ./tests/src/Common/test_dependencies/test_dependencies.csproj --packages /opt/code/packages/"
              buildCommands += """${dockerCommand} bash -c \"for test in \\\$(cat ./tests/testsRunningInsideARM.txt | sed s'/.\$//') ; \\
              do ${getDotnetPath(branch)} build -c ${configuration} ./tests/src/\\\${test}.*proj \\
              /p:BuildOS=Linux /p:TargetFrameworkIdentifier=.NETStandard /p:CSharpCoreTargetsPath=Roslyn/Microsoft.CSharp.Core.targets ; done\""""

              buildCommands += "${dockerCommand} chown -R \$(id -u):\$(id -g) ./bin"

              // arm32_ci_test.sh expects that tests are built for Windows_NT
              buildCommands += "mv ./bin/tests/Linux.x64.${configuration} ${xUnitTestBinBase}"

              // Unpack the corefx binaries
              String coreFxBinDir = "./bin/CoreFxBinDir"
              buildCommands += "mkdir ${coreFxBinDir}"
              buildCommands += "tar -xf ./${getCorefxBinPath(branch)}/build.tar.gz -C ${coreFxBinDir}"

              // Create Core_Root
              buildCommands += "mkdir -p ${xUnitTestBinBase}/Tests/Core_Root"

              // As we applied patch for release/2.0.0 we need to clean up repo as test scripts except
              if (branch == 'release/2.0.0') {
                buildCommands += "git checkout ."
                buildCommands += "git clean -f"
              }
            }

            // Call the ARM CI script to cross build and test using docker
            String buildCommand = """./tests/scripts/arm32_ci_script.sh \\
            --mode=docker \\
            --${architecture} \\
            --linuxCodeName=${linuxCodeName} \\
            --buildConfig=${lowerConfiguration} """

            if (isBuildOnly) {
              buildCommand += "--skipTests"
            }
            else {
              buildCommand += """--testRootDir=${xUnitTestBinBase} \\
              --coreFxBinDir=./bin/CoreFxBinDir \\
              --testDirFile=./tests/testsRunningInsideARM.txt"""
            }

            buildCommands += buildCommand

            // Basic archiving of the build, no pal tests
            addArchival(newJob, "bin/Product/**,bin/obj/*/tests/**/*.dylib,bin/obj/*/tests/**/*.so", "bin/Product/**/.nuget/**")
            break
          default:
            assert false : "Unknown architecture: '${architecture}'"
        }
        break
      default:
        assert false : "Unknown os: '${os}'"
        break
    }

    return buildCommands
  }

  def static addPRTriggers(def job, String branch, String architecture, String os, String configuration, String scenario) {
    String contextString = ""
    String triggerString = ""
    boolean isDefaultTrigger = false

    switch (architecture) {
      case 'armel':
        contextString = "${os} ${architecture} Cross ${configuration}"
        triggerString = "(?i).*test\\W+${os}\\W+${architecture}\\W+Cross\\W+${configuration}"

        if (scenario == 'innerloop') {
          contextString += " Innerloop"
          triggerString += "\\W+Innerloop"
        }
        else {
          contextString += " ${scenario}"
          triggerString += "\\W+${scenario}"
        }

        if (configuration == 'Checked') {
          contextString += " Build and Test"
          triggerString += "\\W+Build and Test"
        }
        else {
          contextString += " Build"
          triggerString += "\\W+Build"
        }

        triggerString += ".*"
        break

      default:
        assert false : "Unknown architecture: '${architecture}'"
        break
    }

    // Now determine what kind of trigger this job needs, if any. Any job should be triggerable, except for
    // non-flow jobs that are only used as part of flow jobs.

    switch (architecture) {
      case 'armel':
        switch (os) {
          case 'Tizen':
            if (scenario == 'normal') {
              if (configuration == 'Checked') {
                isDefaultTrigger = true
              }
            }
            break
        }

        break

      default:
        assert false : "Unknown architecture: '${architecture}'"
        break
    }

    if (isDefaultTrigger) {
        Utilities.addGithubPRTriggerForBranch(job, branch, contextString)
    }
    else {
        Utilities.addGithubPRTriggerForBranch(job, branch, contextString, triggerString)
    }
  }

  def static setJobTimeout(def newJob, String architecture, String configuration, String scenario) {
    int timeout = 120

    if (configuration == 'Debug') {
      // Debug runs can be very slow. Add an hour.
      timeout += 60
    }

    Utilities.setJobTimeout(newJob, timeout)
  }
}

class ProjectCorefx extends Project {
  def setupJob(def newJob, String branch, String scenario, boolean isPR, String architecture, String configuration, String os) {
    String osGroup = getOSGroup(os)
    String linuxCodeName = os.toLowerCase()

    newJob.with {
      steps {
        // Call the arm32_ci_script.sh script to perform the cross build of native corefx
        String script = "./cross/arm32_ci_script.sh --buildConfig=${configuration.toLowerCase()} --${architecture} --linuxCodeName=${linuxCodeName} --verbose"
        shell(script)

        // Tar up the appropriate bits.
        shell("tar -czf ${getCorefxBinPath(branch)}/build.tar.gz --directory=\"${getCorefxBinPath(branch)}/runtime/netcoreapp-${osGroup}-${configuration}-${architecture}\" .")
      }
    }

    Utilities.standardJobSetup(newJob, name, isPR, "*/${branch}")

    String archiveContents = "${getCorefxBinPath(branch)}/build.tar.gz"
    addArchival(newJob, archiveContents)

    // Set up triggers
    if (isPR) {
      // We run Tizen Debug and Linux Release as default PR builds
      if (os == "Tizen" && configuration == "Debug") {
        Utilities.addGithubPRTriggerForBranch(newJob, branch, "${os} ${architecture} ${configuration} Build")
      }
      else {
        Utilities.addGithubPRTriggerForBranch(newJob, branch, "${os} ${architecture} ${configuration} Build", "(?i).*test\\W+${os}\\W+${architecture}\\W+${configuration}.*")
      }
    }
    else {
      // Set a push trigger
      Utilities.addGithubPushTrigger(newJob)
    }
  }
}

class ProjectCoreSetup extends Project {
  def setupJob(def newJob, String branch, String scenario, boolean isPR, String architecture, String configuration, String os) {
    String buildCommand = ''
    String osForGHTrigger = os
    String version = "latest-or-auto"
    String dockerWorkingDirectory = "/src/core-setup"
    String buildArgs = "-ConfigurationGroup=${configuration} -TargetArchitecture=${architecture}"

    if (configuration == 'Release') {
      buildArgs += " -strip-symbols"
    }

    if (os == 'Tizen') {
      String dockerCommand = "docker run -e ROOTFS_DIR=/crossrootfs/${architecture}.tizen.build --rm -v \${WORKSPACE}:${dockerWorkingDirectory} -w=${dockerWorkingDirectory} ${Utilities.getDockerImage(branch)}"
      buildArgs += " -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true -- /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json /p:OutputRid=${Utilities.getTizenVersion(branch)}-${architecture}"
      buildCommand = "${dockerCommand} ./build.sh ${buildArgs}"
    }
    else {
      assert false : "Unknown os: '${os}'"
    }

    newJob.with {
      // Set the label.
      steps {
        shell(buildCommand)
      }
    }

    Utilities.standardJobSetup(newJob, name, isPR, "*/${branch}")

    Utilities.addGithubPRTriggerForBranch(newJob, branch, "${osForGHTrigger} ${architecture} ${configuration} Build")

    String archiveString = ["tar.gz", "zip", "deb", "msi", "pkg", "exe", "nupkg"].collect { "${getBinDir(branch)}/*/packages/*.${it},${getBinDir(branch)}/*/corehost/*.${it}" }.join(",")
    addArchival(newJob, archiveString)
  }

  static String getBinDir(String branch)
  {
    switch (branch) {
      case 'release/2.0.0':
      case 'release/2.1':
      case 'release/2.2':
        return 'Bin'
      case 'release/3.0':
      case 'master':
        return 'bin'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  boolean shouldGenerateJob(String scenario, boolean isPR, String architecture, String configuration, String os) {
    // Generate only PR jobs
    return isPR
  }
}

def projects = [
  new ProjectCoreclr(
    name: 'dotnet/coreclr',
    branchList: ['master', 'release/2.0.0', 'release/2.1', 'release/2.2', 'release/3.0'],
    scenarioList: ['normal'],
    configurationList: ['Debug', 'Checked', 'Release']
  ),

  new ProjectCorefx(
    name: 'dotnet/corefx',
    branchList: ['master', 'release/2.0.0', 'release/2.1', 'release/2.2', 'release/3.0'],
    scenarioList: ['normal'],
    configurationList: ['Debug', 'Release']
  ),

  new ProjectCoreSetup(
    name: 'dotnet/core-setup',
    branchList: ['master', 'release/2.0.0', 'release/2.1', 'release/2.2', 'release/3.0'],
    scenarioList: ['normal'],
    configurationList: ['Release']
  )
]


folder(Project.ROOT_FOLDER)

projects.each { project ->
  String projFolderName = project.getProjectFolderName()
  folder(projFolderName) {}

  project.branchList.each { branch ->
    [true, false].each { isPR ->
      project.scenarioList.each { scenario ->
        project.archList.each { architecture ->
          project.configurationList.each { configuration ->
            project.osList.each { os ->
              if (!project.shouldGenerateJob(scenario, isPR, architecture, configuration, os)) {
                return
              }

              assert os == 'Tizen' : 'Now only Tizen is supported'

              String newJobName = Project.getJobName(configuration, architecture, os, scenario)
              String folderName = project.getJobFolderName(branch)

              folder(folderName) {}

              def newJob = job(Utilities.getFullJobName(project.name, newJobName, isPR, folderName)) {}

              project.setupJob(newJob, branch, scenario, isPR, architecture, configuration, os)

              Utilities.fixPermissions(newJob)
            }
          }
        }
      }
    }
  }
}