# Akka Behavior Phases

This project provides an implementation of an abstraction of Akka's typed 
actor behavior, which allows you to describe a behavior in "phases" and 
combine these phases using monadic composition into a single complete 
behavior.

Also included is an example application using the "Dining Hakkers" project 
from [Akka's documentation](https://developer.lightbend.com/start/?group=akka&project=akka-samples-fsm-scala).

## Running the example

```bash
sbt run
```

This will launch a simple command-line interface, which will prompt you to 
choose which implementation of the dining hakkers actor system to run.
You can choose `naive`, `sub-actors`, `fsm` (finite state machine), or 
`behavior-phase`. You can then start the system by typing:

```
start: name1 name2 name3 name4 name5
```

where `name[N]` is the name of the Nth "hakker".

You can stop and restart the system using the commands `stop` and `start`.
`exit` will stop and exit the program.
