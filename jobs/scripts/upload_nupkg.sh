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

for nupkg in $( find ${nupkg_dir} -iname "*.nupkg" -not -iname "*symbols*" ); do
    ${dotnet_dir}/dotnet nuget push ${nupkg} -s ${feed} -k ${key} || true
done

for nupkg in $( find ${nupkg_dir} -iname "*.symbols.nupkg" ); do
    ${dotnet_dir}/dotnet nuget push ${nupkg} -s ${sfeed} -k ${key} || true
done

