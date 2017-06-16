# Client Time
Allows clients/players set their own time, without affecting the server's

## Dependencies
Client Time requires [PacketGate](https://github.com/CrushedPixel/PacketGate/) as a dependency.
You can get the latest version of PacketGate [here](https://github.com/CrushedPixel/PacketGate/releases/)

## Commands
```
# The one command for all of Client Time.
# 'ctime' can be substituted with 'ptime' that some are more familiar with
/ctime

# Sets the player's/client's time, independent of the server's
# '<time>' can be substituted with any integer larger than 0, or 'day', or 'night'
/ctime set <time>

# Resets the player's/client's time so that it is back in sync with the server's
/ctime reset

# Tells the player/client how many ticks ahead their time is compared to the server's
/ctime status
```

## Permissions
```
# Each grant permission to their respective command name
clienttime.command
clienttime.command.set
clienttime.command.reset
clienttime.command.status
```
