#!/bin/bash

usage()
{
    echo "Usage: $0 [--name=container_name] [--home=jenkins_home]"
    exit 1
}

__name="dotnet-tizen-jenkins"
# As we need to run docker inside jenkins docker container path to jenkins home on host and in container
# should be the same.
__home="/var/jenkins_home"

for i in "$@"
do
    case $i in
    -h|--help)
        usage
        exit 1
        ;;
    --name=*)
        __name=${i#*=}
        ;;
    --home=*)
        __home=${i#*=}
        ;;
    *)
        usage
        exit 1
        ;;
    esac
    shift
done

N_TOTAL_CONTAINER=`docker ps -all -qf name=${__name} | wc -l`
N_RUNNING_CONTAINER=`docker ps -qf name=${__name} | wc -l`

if [[ ${N_TOTAL_CONTAINER} -ne 0 && ${N_RUNNING_CONTAINER} -ne 0 ]]; then
    echo "${__name} is already running"
    exit
fi

if [[ ${N_TOTAL_CONTAINER} -ne 0 ]]; then
    docker restart ${__name}
    exit
fi

DOCKER_OPT=("-v" "/var/run/docker.sock:/var/run/docker.sock")

if [[ -z "${__home}" ]]; then
    DOCKER_OPT+=("-v" "/var/jenkins_home")
else
    echo "Use '${__home}' as a jenkins home directory"
    DOCKER_OPT+=("-v" "${__home}:/var/jenkins_home")
fi

docker run -d --group-add $(getent group docker | awk -F: '{print $3}') --name ${__name} -p 8080:8080 -p 50000:50000 ${DOCKER_OPT[@]} jenkins
