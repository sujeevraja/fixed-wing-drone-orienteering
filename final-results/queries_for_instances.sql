-- The following query selects runs used for comparison tables in the paper.
select instance_path, instance_name, number_of_discretizations from exhaustive where
    optimality_reached = "True" and
    cast(solution_time_in_seconds as decimal(16,2)) >= 60.0 and
    ((instance_path like "%_32_%") or (instance_path like "%_33_%"))
    order by instance_path, instance_name, number_of_discretizations

-- This query selects instances to do a single vs multi threading comparison of I-DSSR.
select instance_path, instance_name, number_of_discretizations from exhaustive where
    cast(number_of_nodes_solved as integer) > 1 and
    optimality_reached = "True"
    order by instance_path, instance_name, number_of_discretizations
