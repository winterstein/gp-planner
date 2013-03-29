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
In DB: 
create user hackworker with password 'hackpwd'; -- This is what the Java assumes, but no need to follow that!
create database nhs with owner hackworker; -- the owner bit is optional

In shell: 
createlang plpgsql nhs
psql -d nhs -f /usr/share/postgresql/9.1/contrib/postgis-1.5/postgis.sql

TEST in DB:
\c nhs
\d
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

#### Population!

Lots of files here:
http://www.gro-scotland.gov.uk/statistics/theme/population/estimates/mid-year/2011/tables.html


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

select dz_code,dz_name,astext(the_geom),popest2011 from datazone_2001_cent limit 10;

select dz_code,dz_name,astext(the_geom) from datazone_2001_bdry where dz_name is not null  limit 10;
Should produce lat long coded data


The centroid data has population estimates (strangely the boundary data does not).
The dz_code is used in the SNS csv files

TODO Keith Alexander may know his way round SNS


#### Postcode datazone

in the DB:
create table postcode_datazone (postcode text, datazone text);
Loading this into the DB is slow :(
psql -d nhs -f /home/daniel/gp-planner/data/raw/SNS_postcode_datazone00393445.sql > pcode.out 


OR
create table datazones (postcode1 char(5), postcode2 char(5), census_area char(12), dz_code char(12), interzone char(12), council_area char(12), created date, deleted date, splitare varchar);

Create a copy of the .txt file without the first line (otherwise the column-names upset the import):
wc SNS_postcode_datazone00393445.txt 
Then -1 from the line-count, and do something like this: (alter the number as approrpiate)
tail -n 192452 SNS_postcode_datazone00393445.txt > SNS_postcode_datazone00393445.csv

copy datazones from '/home/daniel/gp-planner/data/raw/SNS_postcode_datazone00393445.csv' delimiters ',' CSV;

If you get an error about date formatting  -- you made need to do:

set datestyle = 'ISO, DMY';

TODO ?? What do the council numbers mean? How to link them with council names?


### Postcode locations:
Download from http://www.doogal.co.uk/UKPostcodes.php


Or (1st part only) 
http://www.freemaptools.com/download-uk-postcode-lat-lng.htm


Or MySociety

From: http://parlvid.mysociety.org:81/os/ 
Download: ONS Postcode Directory (ONSPD)
TODO convert it to lat/long


### GP Practice Data: from ISD
#### Practice locations

http://www.isdscotland.org/Health-Topics/General-Practice/
http://www.isdscotland.org/Health-Topics/General-Practice/Practices-and-Their-Populations/

Chop the first few lines from Prac_ContactDetails_Jan2013_final
In the database:

create table practice_details (NHSBoardName varchar,PracticeCode varchar,size varchar,name varchar,AddressLine1 varchar,AddressLine2 varchar,AddressLine3 varchar,AddressLine4 varchar,postcode char(12),TelephoneNumber varchar,CHPName varchar,CHPCode varchar,PracticeType varchar,DispensingPractice varchar);

copy practice_details from '/home/daniel/gp-planner/data/isd/Prac_ContactDetails_Jan2013_final.csv' delimiters ',' CSV;

update practice_details set size = replace(size,',','');

create table practice_listsize (PracticeCode varchar, NHSBoardCipher varchar, NHSBoardName varchar, age_all integer, age0_4	varchar, age5_14 varchar, 	age15_24 varchar, 	age25_44 varchar, 	age45_64 varchar, 	age65_74 varchar, 	age75_84 varchar, 	age85up varchar);

copy practice_listsize from '/home/daniel/gp-planner/data/isd/Prac_ListSize_Jan2013_final.csv' delimiters ',' CSV;


##### Drs per Practice

Chop down the GP_ContactDetails xls into a csv with less columns

create table gps (PracticeCode varchar, GMCcode varchar, surname varchar, firstname varchar, postcode char(12));

copy gps from 'GP_ContactDetails_Jan2013_final.csv' delimiters ',' CSV;




Deprecated Alternative -- scrape it
Location:
Scrape it from: http://www.nhs24.com/FindLocal
E.g. 3rd page of postcode=EH7 4LX data:
http://www.nhs24.com/FindLocal?postcode=EH7%204LX&service=GPs&start=3

BUT better to go to ISD who have files on this stuff, with extra info


Practice boundaries scrapable from NHS lothian??


QOF (quality and outcomes framework) data -- gives e.g. number with diabetes & % who had a blood test
Practice level data is gettable with permission
This would give insight into demand & the health-care-aspect of demographics

