#!/bin/bash

project=$1; shift
base_branch=$1; shift
target_branch=$1; shift
version_tag=$1; shift

if [[ -z ${project} || -z ${base_branch} || -z ${target_branch} || -z ${version_tag} ]]; then
    echo "usage: ${0} <project> <base branch> <target branch> <version tag>"
    exit 1
fi

mkdir ${project}
{
    pushd ${project}
    git init
    git remote add origin git@github.sec.samsung.net:dotnet/${project}.git
    git fetch origin ${base_branch}:${base_branch}
    git fetch origin ${target_branch}:${target_branch}
    popd
}

patch="${project}-${version_tag}.patch"
git -C ${project} diff ${base_branch} ${target_branch} > ${patch}

