# Setup
You must configure the akka-cluster.yml file as well as the Makefile to point to the correct remote cluster. Currently it is expected that we will be working with 3 nodes, but this should be easily configurable. 

You can run this project through makefile commands. Note that the local environment is not currently completely setup and configured.

# Run 
In order to run, first make sure you setup your environment correctly, then run:
```make deploy```
During your second deployment, when the nodes are up and running in the cluster, we can use:
```make redeploy``` 