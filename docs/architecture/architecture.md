# Architecture 
architecture diagram using mermaid with following structure:  


we use always backend for frontend
    this has frontend spezific rest api
    uses authentication token to get user info
    m

producer frontend 
->
backend for producer frontend
-> 
fhir api service











producer frontend: sends immunization data to
    fhir api service:   
        this splits the data to medical data and personal data, 
        personal data is stored to fhir database
        medical data is send to openehr
openehr stores the data to his database

producer frontend: gets the 


consumer frontend: has the patient id and sends a get request to 
    fhir api service:
        this searches for the patien id,
        gets the personal data,
        gets the openehr id,
        gets the medical data from openehr by openehr id,
        returns the medical data






