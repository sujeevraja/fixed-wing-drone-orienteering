-- This query selects instances with which 1-thread DSSR and 1-thread I-DSSR will be compared.
select instance_path, instance_name, number_of_discretizations from exhaustive where
    optimality_reached = "True"
    and cast(solution_time_in_seconds as decimal(16,2)) >= 60.0
    and cast(solution_time_in_seconds as decimal(16,2)) < 3600.0
    and ((instance_path like "%_32_%") or (instance_path like "%_33_%"))
    order by instance_path, instance_name, number_of_discretizations

-- This query selects instances to do a single vs multi threading comparison of I-DSSR.
select instance_path, instance_name, number_of_discretizations from exhaustive where
    optimality_reached = "True"
    and cast(number_of_nodes_solved as integer) > 1
    and cast(solution_time_in_seconds as decimal(16,2)) < 3600.0
    and instance_path not like "%_66_%"
    order by instance_path, instance_name, number_of_discretizations
