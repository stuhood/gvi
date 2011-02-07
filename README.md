gvi
========

A Java implementation of group varint encoding.

About equivalent in speed to non-grouped encoding (as implemented by Avro 1.4.1)

Recent Results
--------
bin/caliper net.hoodidge.gvi.GroupVarIntBenchmark

            benchmark  us linear runtime
           AvroEncode 322 ==============================
           AvroDecode 114 ==========
    GroupVarIntEncode 112 ==========
    GroupVarIntDecode 139 ============

Building
--------

[sbt](http://code.google.com/p/simple-build-tool/)

