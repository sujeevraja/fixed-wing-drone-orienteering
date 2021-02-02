-- RUN-TIME COMPARISON
-- Following query is for comparing run-times of 1-thread simple search, 1-thread interleaved
-- search and concurrent interleaved search.
select
    simple.instance_path,
    simple.instance_name,
    simple.number_of_discretizations,
    simple.solution_time_in_seconds as simple_time,
    simple.optimality_reached as simple_opt_reached,
    one_thread_interleaved.solution_time_in_seconds as one_thread_time,
    one_thread_interleaved.optimality_reached as one_thread_opt_reached,
    exhaustive.solution_time_in_seconds as concurrent_time,
    exhaustive.optimality_reached as concurrent_opt_reached
from simple
join one_thread_interleaved
on
    simple.instance_name = one_thread_interleaved.instance_name and
    simple.number_of_discretizations = one_thread_interleaved.number_of_discretizations
join exhaustive
on
    simple.instance_name = exhaustive.instance_name and
    simple.number_of_discretizations = exhaustive.number_of_discretizations
order by
    simple.instance_path,
    simple.instance_name,
    simple.number_of_discretizations

-- EFFECT OF DOMINANCE RULE RELAXATION
-- Following query is to compare the use of dominance rule relaxation in 1-thread runs.
select
    strict_dominance_enforced.instance_path,
    strict_dominance_enforced.instance_name,
    strict_dominance_enforced.number_of_discretizations,
    strict_dominance_enforced.solution_time_in_seconds as strict_time,
    strict_dominance_enforced.optimality_reached as strict_opt,
    one_thread_interleaved.solution_time_in_seconds as relaxed_time,
    one_thread_interleaved.optimality_reached as relaxed_opt
from strict_dominance_enforced
join one_thread_interleaved
on
    strict_dominance_enforced.instance_name = one_thread_interleaved.instance_name and
    strict_dominance_enforced.number_of_discretizations = one_thread_interleaved.number_of_discretizations
order by
    strict_dominance_enforced.instance_path,
    strict_dominance_enforced.instance_name,
    strict_dominance_enforced.number_of_discretizations

-- EXHAUSTIVE RUN RESULTS
-- Following query is to show exhaustive stats of runs that are feasible and reach optimality.
select * from exhaustive
where
	optimality_reached = "True" and
	cast(root_upper_bound as decimal(16,2)) > 0.0

