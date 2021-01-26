import matplotlib.pyplot as plt
import numpy as np
import csv
import math
import statistics
import os

def set_box_color(bp, color, ls):
    plt.setp(bp['boxes'], color='black', ls=ls, lw=1)
    plt.setp(bp['whiskers'], color='black', lw=1)
    plt.setp(bp['caps'], color='black', lw=1)
    plt.setp(bp['medians'], lw=1)
    for patch, c in zip(bp['boxes'], color):
        patch.set_facecolor(color)

f = open('threading.csv', 'r')
reader = csv.reader(f)
rows = []
for row in reader:
    rows.append(row)

times = {i : [] for i in range(0, 3)}

for i in range(1, len(rows)):
    times[0].append(float(rows[i][0]))
    times[1].append(float(rows[i][1]))
    times[2].append(float(rows[i][2]))

to_plot = [times[0], times[1], times[2]]
labels = [r'\begin{center} \noindent Single-threaded\\ \noindent B\&P with DSSR \end{center}', 
          r'\begin{center} \noindent Single-threaded\\ \noindent B\&P with I-DSSR \end{center}', 
          r'\begin{center} \noindent Multi-threaded\\ B\&P with I-DSSR \end{center}']

plt.rc('font',**{'family':'serif','serif':['Palatino']})
plt.rc('text', usetex=True)
plt.style.use('seaborn-paper')

fig, ax = plt.subplots(figsize=(4,5))
bplot = ax.boxplot(to_plot, vert=True, patch_artist=True, widths=0.2, labels=labels) 
colors = ['pink', 'lightblue', 'lightgreen']

for patch, color in zip(bplot['boxes'], colors):
    patch.set_facecolor(color)
    
ax.set_yscale('log')
ax.set_ylabel(r'Computation time in seconds')
ax.xaxis.set_ticks_position('none') 
plt.savefig('threading_comparison_bar.pdf', format='pdf')


f = open('dominance.csv', 'r')
reader = csv.reader(f)
rows = []
for row in reader:
    rows.append(row)

strict_times = []
relaxed_times = []

for i in range(1, len(rows)):
    strict_times.append(float(rows[i][0]))
    relaxed_times.append(float(rows[i][1]))
    
bins = [np.min(strict_times)]
cur_value = bins[0]
multiplier = 1.2
while cur_value < np.max(strict_times):
    cur_value = cur_value * multiplier
    bins.append(cur_value)
bins = np.array(bins)

fig, ax = plt.subplots()
n, bins, patches = plt.hist(strict_times, bins=bins, density=True, alpha=0.5, edgecolor='black', color='blue', facecolor='g')

bins = [np.min(relaxed_times)]
cur_value = bins[0]
multiplier = 1.2
while cur_value < np.max(strict_times):
    cur_value = cur_value * multiplier
    bins.append(cur_value)
bins = np.array(bins)

n, bins, patches = plt.hist(strict_times, bins=bins, density=True, alpha=0.5, edgecolor='black', color='blue', facecolor='b')


ax.set_xscale('log')
plt.savefig('dominance_comparison_scatter.pdf', format='pdf')