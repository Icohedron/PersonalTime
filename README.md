# Personal Time
Allows players set their own personal time of day which does not affect everyone else's!

Say it's day, but you want to get that epic night-time screenshot! But didn't want to wait for night to fall?
Or maybe you want it to be sunrise, to witness the beauty of the sun! But you didn't want to have to wait for it?
Well, now you can do that very easily! And, even better, only you can see the change in time! Using this plugin, players can set their own time of day that only he or she can see! Everyone else will see the normal flow of time (unless, of course, they also set their own time).

## Dependencies
Client Time requires [PacketGate](https://github.com/CrushedPixel/PacketGate/) as a dependency.
You can get the latest version of PacketGate [here](https://github.com/CrushedPixel/PacketGate/releases/)

## Commands
```
# The one command for all of Client Time.
/ptime

# Sets the player's/client's time, independent of the server's
# '<time>' can be substituted with any integer larger than 0, or 'day', or 'night'
/ptime set <time>

# Resets the player's/client's time so that it is back in sync with the server's
/ptime reset

# Tells the player/client how many ticks ahead their time is compared to the server's
/ptime status
```

## Permissions
```
# Each grant permission to their respective command name
personaltime.command
personaltime.command.set
personaltime.command.reset
personaltime.command.status
```
