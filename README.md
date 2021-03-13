# etcd-playground

## Build

`./mvnw clean package`

## Use

```bash
docker run -it -p 2379:2379 gcr.io/etcd-development/etcd:v3.4.7 \
  etcd \
  --name etcd0 \
  --listen-client-urls http://0.0.0.0:2379 \
  --advertise-client-urls http://0.0.0.0:2379
```

```bash
java -cp target/frosnode-jar-with-dependencies.jar de.frosner.commands.Start
```
