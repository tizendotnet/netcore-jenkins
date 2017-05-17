
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

    /**
     * Get the folder name for a job.
     *
     * @param project Project name (e.g. dotnet/coreclr)
     * @return Folder name for the project. Typically project name with / turned to _
     */
    def static getFolderName(String project) {
        return project.replace('/', '_')
    }

}
