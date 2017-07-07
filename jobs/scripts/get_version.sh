#!/bin/bash

project=$1; shift
version=$1; shift
minor_version=$1; shift
patch=$1; shift

convert_builddate()
{
    local _version=$1; shift

    IFS=- read -r branch date count <<< ${_version}
    local month=${date::(-2)}
    local day=${date:(-2)}

    # The most significant digits represents the month count since April 1996.
    # In the example above 249 represents Jan 2017.
    local byear=2017
    local bmonth=249

    if (( month >= bmonth )); then
        local diff=$(( ${month} - ${bmonth} ))
        local diff_year=$(( ${diff} / 12 ))
        local diff_month=$(( ${diff} % 12 ))

        builddate="$(( ${byear} + ${diff_year} ))$( printf "%02d" $(( ${diff_month} + 1 )) )${day}-${count}"
    else
        builddate="${date}-${count}"
    fi

    echo ${builddate}
}

pkglist=( "coreclr:Microsoft.NETCore.Runtime.CoreCLR:version.txt"
          "corefx:Microsoft.NETCore.Platforms:version.txt"
          "core-setup:Microsoft.NETCore.App:Microsoft.NETCore.App.versions.txt"
        )

for list in ${pkglist[@]}; do
    IFS=: read -r pkg pkgname version_file <<< ${list}
    if [ "${pkg}" == "${project}" ]; then
        break
    fi
done

fullversion="${version}-${minor_version}"
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

prop_dir="${project}/${version}/${minor_version}"
prop_file="${prop_dir}/build_dev.properties"

if [ -d "${prop_dir}" ]; then
    rm -rf "${prop_dir}"
fi
mkdir -p "${prop_dir}"

echo "version=${version}" >> "${prop_file}"
echo "minor_version=${minor_version}" >> "${prop_file}"
echo "sha1=${commit}" >> "${prop_file}"
echo "buildid=$( convert_builddate "${minor_version}" )" >> "${prop_file}"
echo "patch=${patch}" >> "${prop_file}"

rm -rf ${temp_dir}
rm -rf ${nupkg_name}

exit 0
