def checkoutRepo(String url = env.GIT_REPO_URL, String branch = env.GIT_BRANCH) {
    git url: url, branch: branch
}

def setupPythonEnv(String venvDir = env.PYTHON_ENV, String venvPath = env.VENV_PATH) {
    sh "python3 -m venv ${venvDir}"
    sh """
        . "${venvPath}/activate" && \
        pip install --upgrade pip && \
        pip install -r requirements.txt || true
    """
}

def runSonarScanner(
    String venvPath = env.VENV_PATH,
    String projectKey = env.SONAR_PROJECT_KEY,
    String sonarHost = env.SONAR_HOST_URL,
    String sonarEnv = env.SONARQUBE_ENV
) {
    withSonarQubeEnv(sonarEnv) {
        withCredentials([string(credentialsId: 'Pravalika-sonar-creds', variable: 'SONAR_TOKEN')]) {
            sh """
                . "${venvPath}/activate" && \
                sonar-scanner \\
                  -Dsonar.projectKey=${projectKey} \\
                  -Dsonar.sources=. \\
                  -Dsonar.host.url=${sonarHost} \\
                  -Dsonar.login=${SONAR_TOKEN}
            """
        }
    }
}

def notify(String status, String slackChannel, String emailRecipients, String sonarReportUrl) {
    def colors = [
        SUCCESS: 'good',
        FAILURE: 'danger'
    ]
    def icons = [
        SUCCESS: ':large_green_circle:',
        FAILURE: ':red_circle:'
    ]
    def subjects = [
        SUCCESS: "SUCCESS Jenkins Job '${env.JOB_NAME} [#${env.BUILD_NUMBER}]'",
        FAILURE: "FAILURE Jenkins Job '${env.JOB_NAME} [#${env.BUILD_NUMBER}]'"
    ]
    def messages = [
        SUCCESS: 'Job completed successfully!',
        FAILURE: 'Job failed. Please check logs.'
    ]

    def slackMessage = """
${icons[status]} *${status}*
*Status:* ${messages[status]}
*Job:* \`${env.JOB_NAME}\`
*Build Number:* #${env.BUILD_NUMBER}
:link: *Build URL:* <${env.BUILD_URL}|Click to view build>
:page_facing_up: *SonarQube Report:* <${sonarReportUrl}|View Report>
"""

    def emailMessage = """
<html>
<body>
    <h3>Status: ${status}</h3>
    <p><b>${messages[status]}</b></p>
    <p><b>Job:</b> ${env.JOB_NAME}</p>
    <p><b>Build Number:</b> #${env.BUILD_NUMBER}</p>
    <p><b>Build URL:</b> <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
    <p><b>SonarQube Report:</b> <a href="${sonarReportUrl}">View Report</a></p>
</body>
</html>
"""

    slackSend(
        channel: slackChannel,
        color: colors[status],
        message: slackMessage
    )

    mail(
        to: emailRecipients,
        subject: subjects[status],
        body: emailMessage,
        mimeType: 'text/html'
    )
}
