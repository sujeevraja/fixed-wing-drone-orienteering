----------------------------------------------------------------------------
-- The following query selects runs used for comparison tables in the paper.
-- The query gives 59 instance/discretization pairs.
-- We do non-exhaustive runs using these pairs.
----------------------------------------------------------------------------
select instance_path, instance_name, number_of_discretizations from exhaustive where
    optimality_reached = "True" and
    cast(solution_time_in_seconds as decimal(16,2)) >= 60.0 and
    ((instance_path like "%_32_%") or (instance_path like "%_33_%"))
    order by instance_path, instance_name, number_of_discretizations
