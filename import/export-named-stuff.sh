#! /bin/sh

echo "COPY (SELECT * from named_nodes) TO STDOUT CSV" |psql osm > /data/work/zorglub/search_exports/named_nodes_20121021.csv
echo "COPY (SELECT * from named_ways) TO STDOUT CSV" |psql osm > /data/work/zorglub/search_exports/named_ways_20121021.csv
