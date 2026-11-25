package pipelines

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
 * The "before" pipeline: a small but realistic TeamCity estate.
 *
 * Deliberate enterprise realities:
 *   - build/test on a shared self-hosted agent pool ("docker-capable")
 *   - secrets live in TeamCity parameters (tc: typed, some in AWS SM
 *     via the teamcity parameter pull) — the classic static-key shape
 *     that migrations have to replace with OIDC
 *   - publish as an artifact + snapshot dependency chain
 *   - manual-gated deploy step, SSH to the cluster
 */

version = "2024.2"

val vcsRoot = DslContext.projectRoot.let {
    GitVcsRoot({
        name = "demo-app"
        url = "https://github.com/repipe-dev/demo-app"
        branch = "refs/heads/main"
    })
}

object BuildAndTest : BuildType({
    name = "Build and Test"
    vcs { root(vcsRoot) }

    params {
        param("env.PYTHON_VERSION", "3.11")
        // The classic shape: long-lived static AWS keys as TC parameters,
        // possibly synced from AWS Secrets Manager by a nightly job.
        password("env.AWS_ACCESS_KEY_ID", "credentialsJSON:aws-ci-key-id")
        password("env.AWS_SECRET_ACCESS_KEY", "credentialsJSON:aws-ci-key-secret")
        param("env.ECR_REPO", "012345678901.dkr.ecr.eu-central-1.amazonaws.com/demo-app")
    }

    steps {
        script {
            name = "Install"
            scriptContent = """
                python -m venv .venv
                . .venv/bin/activate
                pip install -e .[dev]
            """.trimIndent()
        }
        script {
            name = "Test"
            scriptContent = """
                . .venv/bin/activate
                pytest -v --junitxml=results/junit.xml
            """.trimIndent()
        }
        dockerCommand {
            name = "Build image"
            commandType = build {
                source = file { path = "Dockerfile" }
                namesAndTags = "%env.ECR_REPO%:%build.number%"
            }
        }
        dockerCommand {
            name = "Push image"
            commandType = push {
                namesAndTags = "%env.ECR_REPO%:%build.number%"
            }
        }
    }

    features { dockerSupport {} }

    triggers { vcs {} }

    artifactRules = "results/junit.xml"
})

object DeployStaging : BuildType({
    name = "Deploy → staging"
    vcs { root(vcsRoot) }

    dependencies {
        dependency(BuildAndTest) {
            snapshot {}
            artifacts { artifactRules = "results/junit.xml" }
        }
    }

    steps {
        script {
            name = "kubectl apply"
            scriptContent = """
                kubectl -n demo-staging set image deploy/demo-app \
                    demo-app=%env.ECR_REPO%:%dep.BuildAndTest.build.number%
                kubectl -n demo-staging rollout status deploy/demo-app
            """.trimIndent()
        }
    }
})
