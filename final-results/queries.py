def generate_exhaustive_table_count_query(category='optimal', num_targets=21, num_discretizations=2):
    query = 'select count(*) from exhaustive_dssr_bit_based_cycles where '
    query += 'cast(number_of_discretizations as integer) = {} and '.format(num_discretizations)
    query += 'instance_path like "%{}%" and '.format(num_targets)
    if category == 'optimal':
        query += 'cast(solution_time_in_seconds as decimal(16,2)) < 3600.0'
    if category == 'timed-out':
        query += 'cast(solution_time_in_seconds as decimal(16,2)) >= 3600.0'
    return query

def generate_exhaustive_opt_table_query(num_targets=21, discretizations=[2, 4, 6]):
    return """
        select 
	        ex_1.instance_name, 
            printf("%.2f", ex_1.root_upper_bound) as rub_2,
            printf("%.2f", ex_1.final_lower_bound) as opt_2,
            printf("%d", ex_1.number_of_nodes_solved) as nodes_2,
            printf("%.2f", ex_1.solution_time_in_seconds) as time_2,
            printf("%.2f", ex_1.root_lower_bound) as rlb_2,
            printf("%.2f", ex_2.root_upper_bound) as rub_4,
            printf("%.2f", ex_2.final_lower_bound) as opt_4,
            printf("%d", ex_2.number_of_nodes_solved) as nodes_4,
            printf("%.2f", ex_2.solution_time_in_seconds) as time_4,
            printf("%.2f", ex_2.root_lower_bound) as rlb_4,
            printf("%.2f", ex_3.root_upper_bound) as rub_6,
            printf("%.2f", ex_3.final_lower_bound) as opt_6,
            printf("%d", ex_3.number_of_nodes_solved) as nodes_6,
            printf("%.2f", ex_3.solution_time_in_seconds) as time_6,
            printf("%.2f", ex_3.root_lower_bound) as rlb_6
        from exhaustive_dssr_bit_based_cycles as ex_1
        inner join exhaustive_dssr_bit_based_cycles as ex_2
        inner join exhaustive_dssr_bit_based_cycles as ex_3
        on ex_1.instance_name = ex_2.instance_name and ex_1.instance_name = ex_3.instance_name
        where
            ( (ex_1.optimality_reached = "True" and cast(ex_1.root_lower_bound as decimal(16,2)) > 0.0) or
            (ex_2.optimality_reached = "True" and cast(ex_2.root_lower_bound as decimal(16,2)) > 0.0) or 
            (ex_3.optimality_reached = "True" and cast(ex_3.root_lower_bound as decimal(16,2)) > 0.0) ) and 
            cast(ex_1.number_of_discretizations as integer) = {1} and 
            ex_1.instance_path like "%{0}%" and 
            cast(ex_2.number_of_discretizations as integer) = {2} and 
            ex_2.instance_path like "%{0}%" and 
            cast(ex_3.number_of_discretizations as integer) = {3} and 
            ex_3.instance_path like "%{0}%"
        order by ex_1.instance_name
        """.format(num_targets, discretizations[0], discretizations[1], discretizations[2])

def generate_idssr_query():
    return """
        select
            simple.instance_name,
            simple.number_of_discretizations,
            simple.solution_time_in_seconds as simple_time,
            simple.optimality_reached as simple_opt_reached,
            one_thread_interleaved.solution_time_in_seconds as one_thread_time,
            one_thread_interleaved.optimality_reached as one_thread_opt_reached
        from simple
        join one_thread_interleaved
        on
            simple.instance_name = one_thread_interleaved.instance_name and
            simple.number_of_discretizations = one_thread_interleaved.number_of_discretizations
        where
            one_thread_interleaved.optimality_reached = "True" and cast(one_thread_interleaved.root_lower_bound as decimal(16,2)) > 0.0
        order by
            simple.instance_name
        """

def generate_concurrency_query():
    return """
        select
            exhaustive_dssr_bit_based_cycles.instance_name,
            cast(substr(exhaustive_dssr_bit_based_cycles.instance_path, 12, 2) as integer) as num_targets,
            cast(one_thread_dssr_bit_based_cycles.number_of_discretizations as integer) as num_discretizations,
            one_thread_dssr_bit_based_cycles.solution_time_in_seconds as one_thread_time,
            one_thread_dssr_bit_based_cycles.optimality_reached as one_thread_opt_reached,
            exhaustive_dssr_bit_based_cycles.solution_time_in_seconds as concurrent_time,
            exhaustive_dssr_bit_based_cycles.optimality_reached as concurrent_opt_reached,
            cast(exhaustive_dssr_bit_based_cycles.maximum_parallel_solves as integer) as max_concurrent_solves
        from one_thread_dssr_bit_based_cycles
        join exhaustive_dssr_bit_based_cycles
        on
            one_thread_dssr_bit_based_cycles.instance_name = exhaustive_dssr_bit_based_cycles.instance_name and
            one_thread_dssr_bit_based_cycles.number_of_discretizations = exhaustive_dssr_bit_based_cycles.number_of_discretizations
        where 
            one_thread_dssr_bit_based_cycles.optimality_reached = "True" and 
			cast(one_thread_dssr_bit_based_cycles.solution_time_in_seconds as float) > 5.0
        order by
            one_thread_dssr_bit_based_cycles.instance_name
        """