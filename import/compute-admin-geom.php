<?php
  /**
   * Compute the polygon of each administrative boundary,
   * using Jocelyn Jaubert's magic create_polygon() recursive function for nested 
   * relations.
   */

    include("config.php.inc");
    include("lib/sighandler.inc.php");
    include("lib/timeutils.inc.php");
    include("lib/dbutils.inc.php");
  
    connect($db_conn_string, $db_search_path);

    safe_dml_query("create table if not exists admin_geom (".
            "relation_id INTEGER, name TEXT, needs_compute INTEGER, tags hstore)");
    dml_query("SELECT AddGeometryColumn('', 'admin_geom', 'geom', 4326, 'GEOMETRY', 2);");
    dml_query("SELECT AddGeometryColumn('', 'admin_geom', 'geom_dump', 4326, 'GEOMETRY', 2);");

    //CREATE INDEX idx_city_geom_geog ON city_geom using gist(geog);
    //CREATE INDEX idx_city_geom_geom_dump ON city_geom using gist(geom_dump);
    //CREATE INDEX idx_city_geom_relation_id ON city_geom using btree(relation_id);
   // Bourgogne:27768 
   // France 11980 - 1362232 metropole - 79981
   // Germany 51477 - 1111111
   // Quebec 61549
    $result = pg_query("SELECT r.id, SUM(case when rm.member_type='R' then 1 else 0 end) as nested from relations r inner join relation_members rm on rm.relation_id = r.id where r.tags -> 'boundary' = 'administrative' GROUP by r.id ORDER BY r.id") or die("Query failed");
    $beg = microtime(true);
    $count = 0;
    $total_nested = 0;
    $errors = Array();
    $num = pg_num_rows($result);
//    while (false) {
    while ($line = pg_fetch_array($result, null, PGSQL_ASSOC)) {
        $id = $line["id"];
        $nested = $line["nested"];
        $t = round( (microtime(true) - $beg) * 1000);
        echo "Relation $id (nested=$nested) ($count/$num - $t ms total - ".count($errors)." errors - $total_nested were nested)\n";
        $count++;

        // INCREMENTAL MODE
        if (pg_num_rows(pg_query("SELECT relation_id from admin_geom where relation_id=$id")) > 0) {
//            echo "Skipping $id\n";
            continue;
        }

        if ($id > 400000) break; // WEIRD STUFF AFTER !

        if ($id == 441315) continue; // Weird stuff, infinite loop
        if ($id == 436174) continue;


    	safe_dml_query("DELETE FROM admin_geom where relation_id=$id");

        if (!$has_linestring_in_ways) {
    	    $ret = dml_query("INSERT INTO admin_geom(needs_compute, relation_id, name, geom, tags) ".
                    "SELECT 1, r.id, MIN(hstore(r.tags) -> 'name'),".
                    " ST_Polygonize(way_geometry.geom) geom FROM way_geometry ".
                    " INNER JOIN relation_members rn on rn.member_id = way_geometry.way_id ".
    		        " INNER JOIN relations r on rn.relation_id = r.id ".
        			   " WHERE rn.member_type='W' AND hstore(r.tags) -> 'admin_level' = '8' AND r.id=$id GROUP BY r.id");
    	} else {
            if ($nested > 0) {
                $total_nested++;
                $ret = dml_query("select create_polygon(".$id.");");
                if (!$ret) {
                    $errors[$id] = pg_last_error();
            	} else {
                    safe_dml_query("INSERT INTO admin_geom(needs_compute, relation_id, name, geom, tags) ".
                        "SELECT 1, r.id, (hstore(r.tags) -> 'name'), ".
                        "(SELECT geom from polygons where id=r.id), ".
                        "r.tags FROM relations r WHERE r.id=".$id);
                }
            } else {
                $ret = dml_query("INSERT INTO admin_geom(needs_compute, relation_id, name, geom, tags) ".
                        "SELECT 1, r.id, MIN(hstore(r.tags) -> 'name'),".
                        " ST_Polygonize(ways.linestring) geom, r.tags FROM ways ".
                        " INNER JOIN relation_members rn on rn.member_id = ways.id ".
                        " INNER JOIN relations r on rn.relation_id = r.id ".
                        " WHERE rn.member_type='W' AND r.id=$id GROUP BY r.id");      
                if (!$ret) {
                    $errors[$id] = pg_last_error();
                }
            }

    	}
    
    }
	foreach ($errors as $id => $err) {
		echo "Failed city $id because of $err\n";
	}

  // safe_dml_query("   UPDATE city_geom SET geom_dump = (ST_Dump(geom)).geom;");
  /* Recompute the polygon of each city as a geography. This will be used for precise computations like city surface */
  // safe_dml_query("update city_geom set geog = (ST_Dump(geom)).geom;");


    /* Second pass: compute parent-child relationships.
     * We do it in several queries, not only in one, because some intersections fail
     */
    // This is how wwe would do it with one
    // create table admin_parent_child as select parent.relation_id as parent_id, child.relation_id as child_id from admin_geom parent inner join admin_geom child on ST_Intersects(parent.geom_dump, child.geom_dump) where char_length(parent.tags->'admin_level') < 3 AND char_length(child.tags->'admin_level') < 3 AND (parent.tags -> 'admin_level')::int < (child.tags -> 'admin_level')::int ;  

    safe_dml_query("create table if not exists admin_parent_child (parent_id bigint, child_id bigint)");
    $result = pg_query("SELECT relation_id from admin_geom where char_length(tags->'admin_level') < 3");
    $beg = microtime(true);
    $count = 0;
    $total_nested = 0;
    $errors = Array();
    $num = pg_num_rows($result);
    while ($line = pg_fetch_array($result, null, PGSQL_ASSOC)) {
        $child_id = $line["relation_id"];
        $t = round( (microtime(true) - $beg) * 1000);
        echo "Compute parents of $child_id ($count/$num - $t ms total - ".count($errors)." errors)\n";
        $count++;
        safe_dml_query("DELETE FROM admin_parent_child WHERE child_id=$child_id",False);

        $ret = dml_query("INSERT INTO admin_parent_child select parent.relation_id as parent_id, child.relation_id as child_id from admin_geom parent inner join admin_geom child on ST_Within(child.geom_dump, parent.geom_dump) where char_length(parent.tags->'admin_level') < 3 AND char_length(child.tags->'admin_level') < 3 AND (parent.tags -> 'admin_level')::int < (child.tags -> 'admin_level')::int AND child.relation_id = $child_id");
        if (!$ret) {
            $errors[$child_id] = pg_last_error();
        }
    }
	foreach ($errors as $id => $err) {
		echo "Failed to compute parents of $id because of $err\n";
	}


?>
