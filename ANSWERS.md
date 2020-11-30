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
|  REGULAR     |          25.1         |           0             |      0       |
|      A       |          71.48        |          0.06           |     0.9      |
|      B       |          16.1         |          0.36           |     0.64     |
|      C       |          24.76        |           0             |     0.48     |
|      D       |          12.92        |          0.47           |     0.53     |
|      E       |          32.79        |          0.63           |     0.34     |
|      F       |          10.04        |           0             |     0.01     |
|      G       |           7.81        |          0.22           |     0.77     |
|      H       |          28.9         |          0.03           |     0.72     |


## Result of analysis

Explain in a few words what you believe is actually happening based on the statistics above.

* FUNKY A: The server is sending many (90%) of the packets out of order, and some packets are lost (6%). 

* FUNKY B: The server does not send about 36% of the packets, resulting in frame skipping. Additionally, 64% of frames are not sent in the correct order, so the video may appear jittery or choppy. This results in low FPS.

* FUNKY C: The server plays about 48% of frames out of order, resulting in jittery/choppy video.

* FUNKY D: The server loses about 47% of packets and sends 53% of packets out of order, resulting in frame skips and jittery playback.

* FUNKY E:
Only sequence numbers of multiples of 3 are sent, so the packet loss is what we'd expect 0.33 and the video looks fast forwarded as the FPS is the same.
* FUNKY F:
The frames are just being sent at a slower rate, making our data rate lower and FPS lower, but we dont have any packet loss or out of order packets as expected.
* FUNKY G:
Like F, the frames are being sent at a slower rate, but we have some occasional packet loss, so our loss is higher than in F.
* FUNKY H:
We have normal playback rate, but some packet so the video gets stuck and jumps forward, and we can observe this with the packet loss being higher.
