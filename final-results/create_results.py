from db_connector import DataBase
from queries import *
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import csv
import math
import statistics
import os
 

db = DataBase('results.db')
num_targets = [21, 32, 33, 64, 66]
num_discretizations = [2, 4, 6]
result_category = ['optimal', 'timed-out']

row_list = []
for t in num_targets:
    row = [t]
    for d in num_discretizations:
        for c in result_category:
            query = generate_exhaustive_table_count_query(category=c, num_targets=t, num_discretizations=d)
            results = db.execute_query(query)
            row.append(results[0][0])
    row_list.append(row)

exhaustive_df = pd.DataFrame(row_list, columns=['num_targets', 'opt_2', 'to_2', 'opt_4', 'to_4', 'opt_6', 'to_6'])

exhaustive_df.to_csv('csv/exhaustive_counts.csv', index=False)

for t in num_targets:
    query = generate_exhaustive_opt_table_query(t)
    df = db.get_dataframe(query)
    columns = df.columns
    for column in columns:
        if ('nodes' in column):
            df[column] = df[column].apply(pd.to_numeric, downcast='integer').fillna(0)
        if (column == 'instance_name'):
            continue 
        else:
            df[column] = df[column].apply(pd.to_numeric, downcast='float').fillna(0)
    for d in num_discretizations:
        time_col_name = 'time_{}'.format(d)
        rub_col_name = 'rub_{}'.format(d)
        rlb_col_name = 'rlb_{}'.format(d)
        opt_col_name = 'opt_{}'.format(d)
        nodes_col_name = 'nodes_{}'.format(d)
        index_list = df.query('time_{} > 3600.00'.format(d)).index
        df.loc[index_list, time_col_name] = 3600.00
        index_list = df.query('time_{} == 3600.00 & nodes_{} == 1 & rub_{} == opt_{}'.format(d,d,d,d)).index
        df.loc[index_list, rub_col_name] = np.nan
        
    for column in columns:
        if ('rlb' in column):
            del df[column]
            continue 
        if (column == 'instance_name' or 'nodes' in column):
            continue 
        else:
            df[column] = df[column].apply(pd.to_numeric, downcast='float')
    df['instance_name'] = df['instance_name'].str.replace(r'.txt$', '', regex=True)
    df.to_csv('csv/full_{}.csv'.format(t), float_format='%.2f', na_rep='--', index=False)

query = generate_concurrency_query()
df = db.get_dataframe(query)
df['instance_name'] = df['instance_name'].str.replace(r'.txt$', '', regex=True)
for column in df.columns:
    if (column == 'instance_name'): 
        continue
    if ('opt' in column):
        del df[column]
        continue
    if (column == 'num_targets'):
        df[column] = df[column].apply(pd.to_numeric, downcast='integer')
        continue
    df[column] = df[column].apply(pd.to_numeric, downcast='float')
df['improvement_factor'] = (df['one_thread_time']-df['concurrent_time'])/df['one_thread_time']*100.00
df.to_csv('csv/one_thread.csv', float_format='%.2f', index=False)

def set_box_color(bp, color, ls):
    plt.setp(bp['boxes'], color='black', ls=ls, lw=1)
    plt.setp(bp['whiskers'], color='black', lw=1)
    plt.setp(bp['caps'], color='black', lw=1)
    plt.setp(bp['medians'], lw=1)
    for patch, c in zip(bp['boxes'], color):
        patch.set_facecolor(color)
        
times = {0 : df['one_thread_time'].tolist(), 1 : df['concurrent_time'].tolist()}

to_plot = [times[0], times[1]]
labels = [r'\begin{center} \noindent Sequential B\&P \\ \noindent (single-threaded)\end{center}', 
          r'\begin{center} \noindent Concurrent B\&P \\ \noindent (multi-threaded) \end{center}']

plt.rc('font',**{'family':'serif','serif':['Palatino']})
plt.rc('text', usetex=True)
plt.style.use('seaborn-paper')

flierprops = dict(markersize=3)
medianprops = dict(linestyle='-', linewidth=1.5, color='firebrick')
fig, ax = plt.subplots(figsize=(4,5))
bplot = ax.boxplot(to_plot, vert=True, patch_artist=True, widths=0.1, labels=labels, flierprops=flierprops, medianprops=medianprops) 
colors = ['lightgray', 'lightgray']

for patch, color in zip(bplot['boxes'], colors):
    patch.set_facecolor(color)
    
ax.set_yscale('log')
ax.set_ylabel(r'Computation time in seconds')
ax.xaxis.set_ticks_position('none') 
plt.savefig('threading_comparison_bar.pdf', format='pdf')
crop_cmd = 'pdfcrop threading_comparison_bar.pdf; mv -f threading_comparison_bar-crop.pdf threading_comparison_bar.pdf'
os.system(crop_cmd)
os.system('mv -f threading_comparison_bar.pdf plots/threading_comparison_bar.pdf')


gaps = df['improvement_factor'].tolist()
xmin = min(times[0])-100
xmax = max(times[0])+100
ymin = min(times[0])-100
ymax = max(times[0])+100

avg_gap = sum(gaps)/len(gaps)
max_gap = max(gaps)

plt.rc('font',**{'family':'serif','serif':['Palatino']})
plt.rc('text', usetex=True)
plt.style.use('seaborn-paper')

# class a instances plot
fig, ax = plt.subplots()
plt.plot([xmin, ymax], [xmin, ymax], '-.k', alpha=0.7, lw=0.3)
plt.scatter(times[0], times[1], marker='o', alpha=0.7, edgecolor='k', linewidths=0.3, s=10)
plt.xlim((xmin, xmax))
plt.ylim((ymin, ymax))
plt.figtext(0.2, 0.8, r'Average computational gain: ' + str(round(avg_gap,2)) + " \%", color='black')
plt.figtext(0.2, 0.73, r'Maximum computational gain: ' + str(round(max_gap,2)) + " \%", color='black')
ax.set_ylabel(r'Concurrent B\&P computation time in seconds')
ax.set_xlabel(r'Sequential B\&P computation time in seconds')
plt.savefig('scatter_time.pdf', format='pdf')
crop_cmd = 'pdfcrop scatter_time.pdf; mv -f scatter_time-crop.pdf scatter_time.pdf'
os.system(crop_cmd)
os.system('mv -f scatter_time.pdf plots/scatter_time.pdf')



