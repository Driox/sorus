import BuildUtil._

addCommandAlias("ls", "projects")
addCommandAlias("cd", "project")
addCommandAlias("c", "compile")
addCommandAlias("ca", "Test / compile")
addCommandAlias("t", "test")
addCommandAlias("r", "run")

addCommandAlias(
  "check",
  "scalafmtSbtCheck; scalafmtCheckAll; Test / compile; scalafixAll --check"
)
addCommandAlias(
  "fmt",
  "Test / compile; scalafixAll; scalafmtSbt; scalafmtAll"
)
addCommandAlias(
  "up2date",
  "reload plugins; dependencyUpdates; reload return; dependencyUpdates"
)
onLoadMessage +=
  s"""|
      |╭─────────────────────────────────╮
      |│     List of defined ${styled("aliases")}     │
      |├─────────────┬───────────────────┤
      |│ ${styled("ls")}          │ projects          │
      |│ ${styled("cd")}          │ project           │
      |│ ${styled("c")}           │ compile           │
      |│ ${styled("ca")}          │ compile all       │
      |│ ${styled("t")}           │ test              │
      |│ ${styled("r")}           │ run               │
      |│ ${styled("check")}       │ checks code style │
      |│ ${styled("fmt")}         │ format code       │
      |│ ${styled("up2date")}     │ dependencyUpdates │
      |╰─────────────┴───────────────────╯""".stripMargin
