#!/bin/bash

lines=`wc -l < $1` 

echo "running: sbatch --output="output/slurm-%A_%a.out" --array=1-$lines%500 slurm-batch-job.sh $1"

sbatch --output="output/slurm-%A_%a.out" --array=1-$lines%500 slurm-batch-job.sh $1

