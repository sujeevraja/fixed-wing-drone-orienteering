-- This query selects instances to do a single vs multi threading comparison of I-DSSR.
select instance_path, instance_name, number_of_discretizations from exhaustive where
    cast(number_of_nodes_solved as integer) > 1 and
    optimality_reached = "True"
    order by instance_path, instance_name, number_of_discretizations