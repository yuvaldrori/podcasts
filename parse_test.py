import sys
import xml.etree.ElementTree as ET
try:
    ET.parse("test.xml")
    print("Parsed ok")
except Exception as e:
    print(f"Error: {e}")
