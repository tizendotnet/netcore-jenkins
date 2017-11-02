#!/usr/bin/env python

import os
import sys
import argparse
import json
import requests
import re
from itertools import groupby

REMAIN_COUNT = 50

class FeedPackageManager:
    url = ""
    packageId = ""
    headers = {}
    feed = {}
    def __init__(self, metadata, key):
        self.url = metadata['Payload']['FeedUrl']
        self.packageId = metadata['Payload']['PackageIdentifier']
        self.headers = { 'Content-Type': 'application/json; charset=utf-8', 'X-NuGet-ApiKey': key }

        # get feed state
        response = requests.get('{0}/api/v2/feed-state'.format(self.url), headers=self.headers)
        print response.text
        self.feed = json.loads(response.text)
        response.close()

    def getVersions(self):
        for package in self.feed['packages']:
            if package['id'] == self.packageId:
                return package['versions']

    def getVersionGroup(self, versions):
        def getVersionTypes(versions):
            pattern = r'(?P<release>\d\.\d\.\d)-(?P<preview>\w+)'
            #pattern = r'(?P<major>\d.\d.\d)-(?P<preview>\b)-(?P<date>\d{5,6})-(?P<count>\d{2})'
            return [key for key, value in groupby(re.findall(pattern, str(versions)))]

        versionTypes = getVersionTypes(versions)
        print "versionTypes: ", versionTypes

        versionGroup = {}
        for versionType in versionTypes:
            group = "{0}-{1}".format(versionType[0], versionType[1])
            versionGroup[group] = []
            for version in versions:
                if group in version:
                    versionGroup[group].append(version)
            versionGroup[group].sort(reverse=True)
        return versionGroup

    def getDeleteVersions(self, versions):
        versionGroup = self.getVersionGroup(versions)
        print "versionGroup: ", versionGroup

        deleteVersions = []
        for group, versions in versionGroup.iteritems():
            print group, versions
            for i in range(REMAIN_COUNT, len(versions)):
                deleteVersions.append(versions[i])
        return deleteVersions

    def deleteOldVersion(self):
        versions = self.getVersions()
        print "versions: ", versions

        deleteVersions = self.getDeleteVersions(versions)
        print "deleteVersions: ", deleteVersions

        for version in deleteVersions:
            print "version: ", version
            response = requests.delete(
                "{0}/api/v2/package/{1}/{2}?hardDelete=true".format(self.url, self.packageId, version),\
                headers=self.headers)

            if response.status_code == 200:
                print('Deleted {0}/{1}'.format(self.packageId, version))
            else:
                print('Delete Failure (code: {0})'.format(response.status_code))
            response.close()


def main():
    op = argparse.ArgumentParser()
    op.add_argument('-m', '--metafile', required=True)
    op.add_argument('-k', '--key', required=True)
    args = op.parse_args()

    print args.metafile
    print args.key

    with open(args.metafile) as f:
        METADATA = json.load(f)
        print METADATA
        fpg = FeedPackageManager(METADATA, args.key)
        fpg.deleteOldVersion()


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as e:
        print(e)
        sys.exit(1)

