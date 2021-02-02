import dubins
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from math import pi

instance_info_filename = 'instance_info.csv'

df = pd.read_csv(instance_info_filename)

instance_name = df['instance_name'][0]
num_targets = int(df['num_targets'][0])
num_vehicles = int(df['num_vehicles'][0])
turn_radius = float(df['turn_radius'][0])
num_discretizations_1 = int(df['num_discretizations_1'][0])
num_discretizations_2 = int(df['num_discretizations_2'][0])
tmax = float(df['tmax'][0])

print('instance name: {}'.format(instance_name))

target_coordinates = []

df = pd.read_csv(instance_name)
for i in range(0, len(df)):
    target_coordinates.append((df['x'][i], df['y'][i]))

x_coords = [coord[0] for coord in target_coordinates]
y_coords = [coord[1] for coord in target_coordinates]
    
h = 2.0 * pi / float(num_discretizations_1)
discretizations = []
for i in range(0, num_discretizations_1):
    discretizations.append(float(i) * h)
    
target_of_vertex = {}
num_vertices = num_targets * num_discretizations_1 
vertices = [[0.0, 0.0, 0.0] for i in range(0, num_vertices)]
for i in range(0, num_targets):
    for j in range(0, num_discretizations_1):
        target_of_vertex[i * num_discretizations_1 + j] = i 
        vertices[i * num_discretizations_1 + j] = [target_coordinates[i][0], 
                                                target_coordinates[i][1], 
                                                discretizations[j]]
        
# plt.rc('font', family='sans-serif')
## for Palatino and other serif fonts use:
plt.rc('font',**{'family':'serif','serif':['Palatino']})
plt.rc('text', usetex=True)

fig, ax = plt.subplots(1, 2)
plt.subplots_adjust(wspace=0.3, hspace=0.3)

vehicles = list(range(1, num_vehicles+1)) 
colors = ['chocolate', 'crimson', 'dimgray', 'orange']
linestyles = ['solid', 'dotted', 'dashed', 'dashdot']


for i in vehicles:
    filename = 'vehicle_' + str(i) + '_1.csv'
    df = pd.read_csv(filename)
    vertex_path = list(df['vertex_path'].values)
    path_x = []
    path_y = []
    for j in range(0, len(vertex_path)-1):
        step_size = 0.5
        s = vertex_path[j]
        t = vertex_path[j+1]
        q0 = tuple(vertices[s])
        q1 = tuple(vertices[t])
        path = dubins.shortest_path(q0, q1, turn_radius)
        configurations, _ = path.sample_many(step_size)
        path_x += [conf[0] for conf in configurations]
        path_y += [conf[1] for conf in configurations]
    ax[0].plot(path_x, path_y, color='black', linewidth=0.6, 
            linestyle=linestyles[i-1], label=r'vehicle '+ str(i) + ' path')

ax[0].tick_params(axis="x", direction="in", labelsize=8)
ax[0].tick_params(axis="y", direction="in", labelsize=8)
ax[0].plot(x_coords[1:-1], y_coords[1:-1], 'r.', markersize=3, label=r'target')
ax[0].plot(x_coords[0], y_coords[0], 'b*', markersize=4, label=r'source')
ax[0].plot(x_coords[-1], y_coords[-1], 'gx', markersize=4, label=r'destination')
ax[0].legend(fontsize=8)
ax[0].set_title(r'$|\Theta| = 6$', fontsize=10)

h = 2.0 * pi / float(num_discretizations_2)
discretizations = []
for i in range(0, num_discretizations_2):
    discretizations.append(float(i) * h)
    
target_of_vertex = {}
num_vertices = num_targets * num_discretizations_2 
vertices = [[0.0, 0.0, 0.0] for i in range(0, num_vertices)]
for i in range(0, num_targets):
    for j in range(0, num_discretizations_2):
        target_of_vertex[i * num_discretizations_2 + j] = i 
        vertices[i * num_discretizations_2 + j] = [target_coordinates[i][0], 
                                                target_coordinates[i][1], 
                                                discretizations[j]]
        
for i in vehicles:
    filename = 'vehicle_' + str(i) + '_2.csv'
    df = pd.read_csv(filename)
    vertex_path = list(df['vertex_path'].values)
    path_x = []
    path_y = []
    for j in range(0, len(vertex_path)-1):
        step_size = 0.5
        s = vertex_path[j]
        t = vertex_path[j+1]
        q0 = tuple(vertices[s])
        q1 = tuple(vertices[t])
        path = dubins.shortest_path(q0, q1, turn_radius)
        configurations, _ = path.sample_many(step_size)
        path_x += [conf[0] for conf in configurations]
        path_y += [conf[1] for conf in configurations]
    ax[1].plot(path_x, path_y, color='black', linewidth=0.6, 
            linestyle=linestyles[i-1], label=r'vehicle '+ str(i) + ' path')

ax[1].tick_params(axis="x", direction="in", labelsize=8)
ax[1].tick_params(axis="y", direction="in", labelsize=8)
ax[1].plot(x_coords[1:-1], y_coords[1:-1], 'r.', markersize=3, label=r'target')
ax[1].plot(x_coords[0], y_coords[0], 'b*', markersize=4, label=r'source')
ax[1].plot(x_coords[-1], y_coords[-1], 'gx', markersize=4, label=r'destination')
ax[1].legend(fontsize=8)
ax[1].set_title(r'$|\Theta| = 2$', fontsize=10)


plt.savefig('illustration.pdf', format='pdf', bbox_inches='tight')



