#!/bin/bash

# If the version is changed, this script returns 0.
# Otherwise, it returns 1. (Including error case.)

PKG_NAME=$1; shift

if [[ -z ${PKG_NAME} ]]; then
    echo "Usage: $0 [PKG_NAME]"
    exit 1
fi

NUGET="nuget.exe"
NUGET_URL="https://dist.nuget.org/win-x86-commandline/latest/"${NUGET}

if [ ! -f ${NUGET} ]; then
    wget ${NUGET_URL}
    if [[ $? != 0 ]]; then
        echo "ERROR: Fail to download ${NUGET}"
        exit 1
    fi
fi

getSHA() {
    local PKG_NAME=$1; shift
    local VERSION=$1; shift
    local PKG_URL="https://dotnet.myget.org/F/dotnet-core/api/v2/package"
    local TEMP_DIR="tmp"

    wget -q -O ${PKG_NAME}.${VERSION}.nupkg ${PKG_URL}/${PKG_NAME}/${VERSION}
    if [[ $? != 0 ]]; then
        echo "ERROR: Fail to download ${PKG_NAME}.${VERSION}.nupkg"
        exit 1
    fi

    unzip -q ${PKG_NAME}.${VERSION}.nupkg -d ${TEMP_DIR}
    chmod +r ${TEMP_DIR}/version.txt

    COMMIT=$( cat ${TEMP_DIR}/version.txt )
    rm -rf ${TEMP_DIR}
    rm -rf ${PKG_NAME}.${VERSION}.nupkg

    echo ${COMMIT}
}

if [ ! -d ${PKG_NAME} ]; then
    mkdir ${PKG_NAME}
fi

SOURCE="https://dotnet.myget.org/F/dotnet-core/api/v3/index.json"

INFO=$( mono ${NUGET} list -Source ${SOURCE} -Prerelease ${PKG_NAME} | head -1 )
echo "INFO: ${INFO}"
VERSION=${INFO##* }
if [ "${VERSION}" == "$( cat ${PKG_NAME}/version )" ]; then
    echo "Version is not changed"
    exit 1
fi

echo "Version changed to ${VERSION}"
echo ${VERSION} > ${PKG_NAME}/version
getSHA "${PKG_NAME}" "${VERSION}" > ${PKG_NAME}/commit

exit 0
