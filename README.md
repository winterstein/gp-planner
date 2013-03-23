gp-planner
==========

NHS Hack Scotland project with Anand &amp; Jo


## HOW TO GET STARTED

### Setup Postgis

Assume: you have postgresql 9.1 installed

sudo apt-get install postgis postgresql-9.1-postgis libpostgis-java

su postgres
In DB: create database nhs

In shell: 
createlang plpgsql nhs
psql -d nhs -f /usr/share/postgresql/9.1/contrib/postgis-1.5/postgis.sql

TEST in DB:
nhs=# \d
               List of relations
 Schema |       Name        | Type  |  Owner   
--------+-------------------+-------+----------
 public | geography_columns | view  | postgres
 public | geometry_columns  | table | postgres
 public | spatial_ref_sys   | table | postgres
(3 rows)


### Data

#### http://www.sns.gov.uk/
Download All Available Data: Download in CSV --> 
and
Download Geography

Which gives you:
SNS\_FullData\_CSV\_14\_3\_2013.zip
SNS\_Geography\_14\_3\_2013.zip




