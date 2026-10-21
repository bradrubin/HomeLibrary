# Project guidance

This project is a Kotlin Android app built with Jetpack Compose, Material 3,
Navigation Compose, Room, and a manual repository/AppContainer architecture.

## Working in this project

- Follow the existing Kotlin, Compose, Room, and Gradle conventions.
- Keep Android app changes scoped to the requested behavior and preserve
  compatibility with the project's configured Android and Kotlin versions.
- The package name is `com.example.bookcatalog`, minSdk is 26, and targetSdk
  follows the project's configured target.
- Run the most relevant configured Gradle build or tests after code changes and
  report any checks that could not be run.
- Do not add dependencies or modify signing, credentials, or release settings
  unless required by the task.
