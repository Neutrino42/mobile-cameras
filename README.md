# mobile-cameras-repast

This repository contains the simulation model of distributed smart mobile cameras for the online multi-object k-coverage problem. \
This model is an adapted version of the following research work:
> L. Esterle and P. R. Lewis, ‘Online Multi-Object k-Coverage with Mobile Smart Cameras’, in Proceedings of the 11th International Conference on Distributed Smart Cameras, New York, NY, USA, 2017, pp. 107–112. doi: 10.1145/3131885.3131909.



This model has been used in https://github.com/digitwins/knowledge-equivalence-DT to serve as the simulation model of a Digital Twin.

The model is written with Repast Symphony (https://repast.github.io/), a well-known simulation platform for the modelling and simulation of multi-agent systems.

This repository provides two model builders. 
- `BasicBuilder` initialises the model with parameters specified in `mobileCameras.rs/parameters.xml`.
- `TraceBasedBuilder` parses an external traces file to initialise the initial state of the simulation. 
Examples of traces files can be found here: https://github.com/digitwins/knowledge-equivalence-DT/tree/main/src/simulator/traces_real
