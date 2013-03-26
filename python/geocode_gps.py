import xml.etree.ElementTree as ET
tree = ET.parse('gp_practice_list.xml')
root = tree.getroot()
from urllib2 import urlopen 
import simplejson
import csv 

search = 'http://unlock.edina.ac.uk/ws/search?format=json&name='

writer = csv.writer(open('../data/gp_practices.csv','w'),delimiter='\t')
sql = [] 

writer.writerow(['town','name', 'lon','phone','street','postcode','lat'])

for practise in root.findall('gp_practice'):
     
    p = {}  

    postcode = practise.findall('postcode')[0].text
    p['postcode'] = postcode 
    p['street'] = practise.findall('poststreet')[0].text
    p['town'] = practise.findall('posttown')[0].text 
    p['phone'] = practise.findall('phone')[0].text
    p['name'] = practise.findall('gp_practice_name')[0].text
    postcode = postcode.replace(' ','%20')

    results = urlopen(search+postcode).read()
    print search+postcode

    json = simplejson.loads(results)
    ll = json['features'][0]['properties']['centroid']
    lon,lat = ll.split(',')
    p['lon'] = lon
    p['lat'] = lat 

    writer.writerow(p.values())

