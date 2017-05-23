#!/bin/bash

project=$1; shift
branch=$1; shift

mkdir -p "${project}/${branch}"

cur_version=$( cat "dotnet-versions/build-info/dotnet/${project}/${branch}/Latest.txt" )
saved_version=$( cat "${project}/${branch}/Latest" 2> /dev/null )

if [  "${saved_version}" == "${cur_version}" ]; then
    echo "Version is not changed"
    exit 1
fi

pkglist=( "coreclr:Microsoft.NETCore.Runtime.CoreCLR"
          "corefx:Microsoft.Private.CoreFx.NETCoreApp"
        )
versionlist=( "coreclr:master:2.1.0"
              "coreclr:release/2.0.0:2.0.0"
              "corefx:master:4.5.0"
              "corefx:release/2.0.0:4.4.0"
            )

for pkg in ${pkglist[@]}; do
    if [ "${pkg%%:*}" == "${project}" ]; then
        pkgname=${pkg##*:}
    fi
done

for list in ${versionlist[@]}; do
    IFS=: read -r pkg br version <<< ${list}
    if [ "${pkg}" == "${project}" ] && [ "${br}" == "${branch}" ]; then
        fullversion="${version}-${cur_version}"
    fi
done

nupkg_name="${pkgname}.${fullversion}.nupkg"
feedlist=( "https://www.nuget.org/packages"
           "https://dotnet.myget.org/F/dotnet-core/api/v2/package"
         )

for feed in ${feedlist[@]}; do
    wget -q -O ${nupkg_name} ${feed}/${pkgname}/${fullversion}
    if [[ $? == 0 ]]; then
        break
    fi
done

if [ ! -f ${nupkg_name} ]; then
    echo "ERROR: Fail to download ${nupkg_name}"
    exit 1
fi

temp_dir="tmp"
if [ -d ${temp_dir} ]; then
    rm -rf ${temp_dir}
fi
mkdir -p "${temp_dir}"
unzip -q ${nupkg_name} -d ${temp_dir}
chmod +r ${temp_dir}/version.txt

echo "Version is changed"
echo "${cur_version}" > "${project}/${branch}/Latest"
echo "$( cat "${temp_dir}/version.txt" 2> /dev/null )" > "${project}/${branch}/Commit"

rm -rf ${temp_dir}
rm -rf ${nupkg_name}

exit 0
