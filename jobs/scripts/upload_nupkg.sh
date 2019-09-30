#!/bin/bash

dotnet_dir=$1; shift
nupkg_dir=$1; shift
feed=$1; shift
sfeed=$1; shift
key=$1; shift

download() {
    local _dir=$1; shift

    local cli_name="dotnet-dev-ubuntu-x64.latest.tar.gz"
    local cli_url="https://dotnetcli.blob.core.windows.net/dotnet/Sdk/master/"${cli_name}

    wget -q ${cli_url}
    if [[ $? != 0 ]]; then
        echo "ERROR: Fail to download dotnet cli"
        exit 1
    fi
    mkdir -p "${_dir}"
    tar xzf "${cli_name}" -C "${_dir}"
}

if [ ! -d ${_dir} ]; then
    download
fi

overall_exit_code=$((0))

nuget_push() {
    local max_count=$((2))
    local counter=$((0))
    local exit_code=$((1))

    while [ $exit_code -ne 0 ] && [ $counter -lt $max_count ]; do
        ${dotnet_dir}/dotnet nuget push ${1} -s ${2} -k ${key} -t 900

        exit_code=$?

        if [ $exit_code -ne 0 ]; then
            echo "Nuget push failed, retrying"
        fi

        counter=$((counter + 1))
    done

    if [ $exit_code -ne 0 ]; then
        echo "Unable to push package ${1}, tried $max_count times"
        overall_exit_code=$((1))
    fi
}

for nupkg in $( find ${nupkg_dir} -iname "*.nupkg" -not -iname "*symbols*" -not -iname "Microsoft.*" -not -iname "transport.Microsoft.*" ); do
    nuget_push ${nupkg} ${feed}
done

for nupkg in $( find ${nupkg_dir} -iname "*.symbols.nupkg" -not -iname "Microsoft.*" -not -iname "transport.Microsoft.*"); do
    nuget_push ${nupkg} ${sfeed}
done

exit ${overall_exit_code}