#!groovy

import jenkins.model.*
import hudson.model.*

def instance = Jenkins.getInstance()

def pluginManager = instance.getPluginManager()
def updateCenter = instance.getUpdateCenter()

def plugins = [
    'build-timeout',
    'credentials-binding',
    'timestamper',
    'ws-cleanup',
    'ant',
    'gradle',
    'workflow-aggregator',
    'github-branch-source',
    'pipeline-github-lib',
    'pipeline-stage-view',
    'git',
    'ssh-slaves',
    'matrix-auth',
    'pam-auth',
    'ldap',
    'email-ext',
    'mailer',
    'oic-auth',
    'saml'
]

def needRestart = false

plugins.each { pluginName ->
    if (!pluginManager.getPlugin(pluginName)) {
        def plugin = updateCenter.getPlugin(pluginName)
        if (plugin) {
            plugin.deploy()
            needRestart = true
        }
    }
}

if (needRestart) {
    instance.save()
}