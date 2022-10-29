This dir contains community code that requires a specific platform and/or environment.

The code **should not** be included in the default Leiningen profile since it would cause problems for most users when running tests, generating docs, etc.

The code **should** be included when generating final jar build for deployment to Clojars, etc.
