def generate_exhaustive_table_count_query(category='optimal', num_targets=21, num_discretizations=2):
    query = 'select count(*) from exhaustive where '
    query += 'cast(number_of_discretizations as integer) = {} and '.format(num_discretizations)
    query += 'instance_path like "%{}%" and '.format(num_targets)
    if category == 'optimal':
        query += 'optimality_reached = "True" and cast(root_lower_bound as decimal(16,2)) > 0.0'
    if category == 'infeasible':
        query += 'optimality_reached = "True" and cast(root_lower_bound as decimal(16,2)) = 0.0'
    if category == 'timed-out':
        query += 'optimality_reached = "False"'
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
        from exhaustive as ex_1
        inner join exhaustive as ex_2
        inner join exhaustive as ex_3
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