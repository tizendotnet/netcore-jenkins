#!/bin/bash
#set -x

project=$1; shift
branch=$1; shift

convert_builddate()
{
    local _version=$1; shift

    # In case of stable, rtm or servicing release, return as is.
    if [ "${_version}" == "stable" ] || [ "${_version}" == "servicing" ] || [ "${_version}" == "rtm" ] ; then
        echo "${_version}"
        exit 0
    fi

    IFS=- read -r branch date count <<< ${_version}
    local month=${date::(-2)}
    local day=${date:(-2)}

    # The most significant digits represents the month count since April 1996.
    # In the example above 249 represents Jan 2017.
    local byear=2017
    local bmonth=249

    local diff=$(( ${month} - ${bmonth} ))
    local diff_year=$(( ${diff} / 12 ))
    local diff_month=$(( ${diff} % 12 ))

    builddate="$(( ${byear} + ${diff_year} ))$( printf "%02d" $(( ${diff_month} + 1 )) )${day}-${count}"
    echo ${builddate}
}

prop_dir="${project}/${branch}"
prop_file="${prop_dir}/build_dev.properties"

if [ -d "${prop_dir}" ]; then
    if [ -f "${prop_file}" ]; then
        source ${prop_file}
    else
        echo "No ${prop_file} file to apply."
    fi
else
    mkdir -p "${project}/${branch}"
fi


cur_version=$( cat "dotnet-versions/build-info/dotnet/${project}/${branch}/Latest.txt" )
nupkg_versions_file="dotnet-versions/build-info/dotnet/${project}/${branch}/Latest_Packages.txt"

if [ "${version}" == "${cur_version}" ]; then
    echo "Version is not changed (${version}). Need to check commit too"
fi

pkglist=( "coreclr:Microsoft.NETCore.Runtime.CoreCLR:version.txt"
          "corefx:Microsoft.Private.CoreFx.NETCoreApp:version.txt"
          "core-setup:Microsoft.NETCore.App:Microsoft.NETCore.App.versions.txt"
        )
versionlist=( "coreclr:master"
              "coreclr:release/2.0.0"
              "coreclr:release/2.1"
              "corefx:master"
              "corefx:release/2.0.0"
              "corefx:release/2.1"
              "core-setup:master"
              "core-setup:release/2.0.0"
              "core-setup:release/2.1"
            )

for list in ${pkglist[@]}; do
    IFS=: read -r pkg pkgname version_file <<< ${list}
    if [ "${pkg}" == "${project}" ]; then
        break
    fi
done

mv ${nupkg_versions_file} ${nupkg_versions_file}.org
tr -d '\r' < ${nupkg_versions_file}.org > ${nupkg_versions_file}

for list in ${versionlist[@]}; do
    IFS=: read -r pkg br major_version <<< ${list}
    while read line; do
        read -r nupkg_name nupkg_version <<< $line
        if [ ${nupkg_name} == ${pkgname} ] && [ "${pkg}" == "${project}" ] && [ "${br}" == "${branch}" ]; then
            fullversion=${nupkg_version}
        fi
    done < ${nupkg_versions_file}
done

nupkg_name="${pkgname}.${fullversion}.nupkg"
feedlist=( "https://www.nuget.org/api/v2/package"
           "https://dotnet.myget.org/F/dotnet-core/api/v2/package"
         )

for feed in ${feedlist[@]}; do
    wget -q -O ${nupkg_name} ${feed}/${pkgname}/${fullversion}
    if [[ $? == 0 ]]; then
        break
    fi
done

temp_dir="tmp"
if [ -d ${temp_dir} ]; then
    rm -rf ${temp_dir}
fi
mkdir -p "${temp_dir}"
unzip -q ${nupkg_name} -d ${temp_dir}
if ! [[ $? == 0 ]]; then
    echo "ERROR: Wrong ${nupkg_name} file"
    rm -rf ${temp_dir}
    rm -rf ${nupkg_name}
    exit 1
fi
chmod +r ${temp_dir}/${version_file}

if [ "${project}" == "core-setup" ]; then
    while read line; do
        if [[ "${line}" =~ "core-setup" ]]; then
            commit=${line##* }
        fi
    done < "${temp_dir}/${version_file}"
else
    commit=$( cat "${temp_dir}/${version_file}" )
fi

if [ "${sha1}" == "${commit}" ] && [ "${version}" == "${cur_version}" ]; then
    echo "Both commit and version are not changed (${commit})"
    exit 1
fi

echo "Version is changed (${version} (${sha1}) -> ${cur_version} (${commit}))"
echo "version=${cur_version}" > "${prop_file}"
echo "sha1=${commit}" >> "${prop_file}"
echo "buildid=$( convert_builddate "${cur_version}" )" >> "${prop_file}"


apache_dir=/var/www/files/
version_dir=${apache_dir}/version-info/${project}/${branch}

mkdir -p ${version_dir}
cp ${prop_file} ${version_dir}/${cur_version}.properties


rm -rf ${temp_dir}
rm -rf ${nupkg_name}

exit 0
