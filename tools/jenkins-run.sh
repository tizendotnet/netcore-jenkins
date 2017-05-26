#!/bin/bash

usage()
{
    echo "Usage: $0 [--name=container_name] [--home=jenkins_home]"
    exit 1
}

__name="dotnet-tizen-jenkins"
__home=

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

DOCKER_OPT=()

if [[ -z "${__home}" ]]; then
    DOCKER_OPT+=("-v" "/var/jenkins_home")
else
    echo "Use '${__home}' as a jenkins home directory"
    DOCKER_OPT+=("-v" "${__home}:/var/jenkins_home")
fi

docker run -d --name ${__name} -p 8080:8080 -p 50000:50000 ${DOCKER_OPT[@]} jenkins
