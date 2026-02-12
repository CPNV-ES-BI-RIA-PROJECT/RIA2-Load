# RIA2-Load
## Structure

```mermaid
sequenceDiagram
    participant O as "Orchestrator"
    participant L as "Load"
    participant B as "Bucket from transform"
    participant BS as "Bucket of Script"
    participant E as "Executor"
    participant M as "MySQL"

    O->>+L: /path/file/to/load
    L->>+B: get /path/file/§to/load
    B->>-L: return file / files
    L->>+BS: PUT script.sql (generate + store)
    L-->>-O: success or failed

    O->>+E: start executor
    E->>+BS: GET script.sql
    E->>+M: execute(script.sql)
    M-->>-E: success or failed
    E-->>-O: success or failed
```
## Question
- Le load doit recevoir les info de manière asynchrone ?
    - exemple : L'orchestrator transmet un path de fichier à récuperer du bucket, le Load le charge et génère un script SQL. Il acquite la fin du processus à l'orchestrateur.
- L'orchestrateur me transmet de path de fichier ou des dossier (les deux ?)