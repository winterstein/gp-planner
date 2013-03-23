gp-planner
==========

NHS Hack Scotland project with Anand & Jo

## The Problem

It takes ~2 years to set up a GP practice.

The lothians has over-loaded GP practices ("open but full"), and this problem
is growing.



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

From
http://www.scotland.gov.uk/Topics/Statistics/sns/SNSRef
download the Postcode Lookup (to get data-zone to postcode data)

##### Geography

For SNS\_Geography, run
shp2pgsql -W LATIN1 DataZone_2001_bdry.shp > DataZone_2001_bdry.sql
shp2pgsql -W LATIN1 DataZone_2001_cent.shp > DataZone_2001_cent.sql

##### How do we convert to lat/long data??
SNS uses Ordnance Survey -- lat long is more useful, eg for output
 -- use ogr2ogr
sudo apt-get install gdal-bin

   ogr2ogr -t_srs EPSG:4236 datazoneboundary_latlong DataZone_2001_bdry.shp
   cd datazoneboundary_latlong
   shp2pgsql -W LATIN1 DataZone_2001_bdry.shp > DataZone_2001_bdry.sql

psql -d nhs -f /home/daniel/gp-planner/data/raw/SNS_Geography_14_3_2013/DataZone_2001_cent.sql
psql -d nhs -f /home/daniel/gp-planner/data/raw/SNS_Geography_14_3_2013/datazoneboundary_latlong/DataZone_2001_bdry.sql

TEST new database tables datazone...
nhs=# \d
                     List of relations
 Schema |            Name            |   Type   |  Owner   
--------+----------------------------+----------+----------
 public | datazone_2001_bdry         | table    | postgres
 public | datazone_2001_bdry_gid_seq | sequence | postgres
 public | datazone_2001_cent         | table    | postgres
 public | datazone_2001_cent_gid_seq | sequence | postgres
 public | geography_columns          | view     | postgres
 public | geometry_columns           | table    | postgres
 public | spatial_ref_sys            | table    | postgres
(7 rows)

select dz_code,dz_name,the_geom as text,popest2011 from datazone_2001_cent limit 10;

select dz_code,dz_name,astext(the_geom) from datazone_2001_bdry where dz_name is not null  limit 10;
Should produce lat long coded data


The centroid data has population estimates (strangely the boundary data does not).
The dz_code is used in the SNS csv files

Keith Alexander may know his way round SNS

#### Postcode datazone

in the DB:
create table postcode_datazone (postcode text, datazone text);
Loading this into the DB is slow :(
psql -d nhs -f /home/daniel/gp-planner/data/raw/SNS_postcode_datazone00393445.sql > pcode.out 


### GP Practice Data

Location:
Scrape it from: http://www.nhs24.com/FindLocal
E.g. 3rd page of postcode=EH7 4LX data:
http://www.nhs24.com/FindLocal?postcode=EH7%204LX&service=GPs&start=3

Practice boundaries scrapable from NHS lothian??


Number of Drs
Number of Patients
 -- This is gettable probably

QOF (quality and outcomes framework) data -- gives e.g. number with diabetes & % who had a blood test
Practice level data is gettable with permission
This would give insight into demand & the health-care-aspect of demographics


