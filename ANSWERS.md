## Observed behaviour

Describe in general words the observed behaviour of each of these servers and 
how it affects the video playback experience. Then explain what you believe is
happening and what is causing the described behaviour.

* FUNKY A:
Starts at sequence number 28.
* FUNKY B:
Server skips many frames.
* FUNKY C:
Server plays frames out of order.
* FUNKY D:
Server plays frames out of order and skips many.
* FUNKY E:
Server skips every few frames, making video fast forward basically.
* FUNKY F:
Server plays back frames slowly
* FUNKY G:
Skips frames and plays frames slowly.
* FUNKY H:
Skips frames occasionally

## Statistics

You may add additional columns with more relevant data.

| FUNKY SERVER | FRAME RATE (pkts/sec) | PACKET LOSS RATE (/sec) | OUT OF ORDER |
|:------------:|-----------------------|-------------------------|--------------|
|  REGULAR     |          25.1             |           0              |       0       |
|      A       |                       |                         |              |
|      B       |                       |                         |              |
|      C       |                       |                         |              |
|      D       |                       |                         |              |
|      E       |                       |                         |              |
|      F       |                       |                         |              |
|      G       |                       |                         |              |
|      H       |                       |                         |              |


## Result of analysis

Explain in a few words what you believe is actually happening based on the statistics above.

* FUNKY A:

* FUNKY B:

* FUNKY C:

* FUNKY D:

* FUNKY E:
Only sequence numbers of multiples of 3 are sent, so the packet loss is what we'd expect 0.33 and the video looks fast forwarded as the FPS is the same.
* FUNKY F:
The frames are just being sent at a slower rate, making our data rate lower and FPS lower, but we dont have any packet loss or out of order packets as expected.
* FUNKY G:
Like F, the frames are being sent at a slower rate, but we have some occasional packet loss, so our loss is higher than in F.
* FUNKY H:
We have normal playback rate, but some packet so the video gets stuck and jumps forward, and we can observe this with the packet loss being higher.
