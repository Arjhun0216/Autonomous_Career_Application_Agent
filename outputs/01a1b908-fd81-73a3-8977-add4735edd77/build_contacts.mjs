import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = "C:/Users/ARJUN/Downloads/Projects/job-platform/outputs/01a1b908-fd81-73a3-8977-add4735edd77";
const filePath = `${outputDir}/HR_Contacts_Screenshot_1.xlsx`;
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(filePath));
const sheet = workbook.worksheets.getItem("HR Contacts");
const table = sheet.tables.items[0];

// The screenshots show source rows 201-350.
const records = [
  ["Mandeep Virdi","mandeep.virdi@esri.in","Head of Human Resources","Esri India"],
  ["Arathi Rajeswari","arathi@suntecgroup.com","AVP, Head of Talent Advancement and Excellence","SunTec Business Solutions"],
  ["Bensley Zachariah","bensely.zachariah@fulcrumdigital.com","Global Head of Human Resources","Fulcrum Digital Inc"],
  ["Ankur Beri","ankur.beri@niit-tech.com","Group Head Human Resources","NIIT Technologies"],
  ["Ramaprasad Baleguli","ramaprasad.baleguli@v2solutions.com","Head of Talent Acquisition-Digital Engineering","V2Solutions"],
  ["Satyodai Krovi","satyodai.krovi@eka1.com","Director, Global Talent Acquisition","Eka Software Solutions"],
  ["Pramodh Karumbaiah","pramodh.karumbaiah@trell.co","Head Of Human Resources","Trell"],
  ["Akanksha Puri","akanksha.puri@sourcefuse.com","Associate Director HR","SourceFuse Technologies"],
  ["Chitra Ravi","chitra.ravi@ample.co.in","Head of HR","Ample Technologies"],
  ["Pritha Das","pritha@near.com","Director - People and Culture","Near"],
  ["Neville Postwalla","neville.postwalla@harbingergroup.com","Associate Vice President - Talent Management","Harbinger Group"],
  ["Arun Singh","arun.singh@puresoftware.com","Senior Director Talent Acquisition","PureSoftware"],
  ["Govind Bhandari","govind.bhandari@daloopa.com","Head of Human Resources & Talent Acquisition","Daloopa"],
  ["Dileep Choyappally","dileep.choyappally@nestgroup.net","Vice President & Head of Human Resources","NeST Digital"],
  ["Sumarani Sarkar","sumarani.y@ekaplus.com","Director - Global Human Resources","Eka Software Solutions"],
  ["Kumar Anchan","kumara@aditiconsulting.com","Director - Recruitment","Aditi Consulting"],
  ["Rena Soans","rena.so@endurance.com","Associate Director - HR Operations","Newfold Digital"],
  ["Gulshan S","gulshan@near.com","Director, Strategic HR","Near"],
  ["Ashwin Singh","ashwin@suki.ai","Head of Talent Acquisition","Suki"],
  ["Donna Ellies","donna.ellies@briq.com","Sr. Director, People Operations - India","Briq"],
  ["Shuchi Nijhawan","shuchi.nijhawan@ekaplus.com","Sr Vice President- Global Human Resources","Eka Software Solutions"],
  ["Sundeep Pandey","sundeep.pandey@puresoftware.com","Director Technology (US Health Care)","PureSoftware"],
  ["Madhan Kumar","madhan.kumar@aditiconsulting.com","Director of Talent Management & Operations","Aditi Consulting"],
  ["Ravi Kumar","ravi.kumar@ample.co.in","Director - Recruitment","Ample Technologies"],
  ["Justin Joseph","justin@near.com","Chief People Officer","Near"],
  ["Nandakishore Padmanabhan","nandakishore.padmanabhan@crmnext.com","Vice President - People & Culture","CRMNEXT"],
  ["Nayana Martin","nayanam@aditiconsulting.com","Associate Director L&D","Aditi Consulting"],
  ["Nivrutha Sampath","nivrutha.s@appviewx.com","VP of HR","AppViewX"],
  ["Ruby Baksi","ruby.baksi@harbingergroup.com","Head Talent Engagement","Harbinger Group"],
  ["Anjali Sharma","anjali.sharma@fulcrumdigital.com","Director, Global head of L&D","Fulcrum Digital Inc"],
  ["Anusha Kishore","anusha.kishore@loco.gg","Assistant Vice President Human Resources","Loco"],
  ["Chiranjeevi Panem","chiranjeevip@byteridge.com","Chief People Officer","Byteridge"],
  ["Sumita Kaushik","sumita.kaushik@blazeclan.com","Associate Director - People & Culture","Blazeclan Technologies"],
  ["Kiran Bala","kiran.bala@blueconchtech.com","Director Human Resources","UST BlueConch Technologies"],
  ["Dineshan Shankar","dshankar@datainensity.com","AVP - HR & Admin","Data Intensity"],
  ["Lavita Nathani","lavita.n@endurance.com","Senior Director Human Resources","Newfold Digital"],
  ["Deepa Mukherjee","deepa.mukherjee@esri.in","Chief People Officer & Vice President","Esri India"],
  ["Sudhir B","sudhir.b@aeriestechnology.com","Head - Talent Acquisition","Aeries Technology Group"],
  ["Ravdeep Singh","ravdeep.singh@sourcefuse.com","Chief People Officer","SourceFuse Technologies"],
  ["Nimmy Chowalloor","nimmy.c@v2solutions.com","Recruitment Head","V2Solutions"],
  ["Divya Prasad","divya.prasad@impelsys.com","Associate Director - Talent Development","Impelsys"],
  ["Devansh Narang","devansh.narang@loco.gg","Head of Content & Talent","Loco"],
  ["Jyothendra Reddy","jyothendraa@aditiconsulting.com","Senior Director - Recruitment & Operations","Aditi Consulting"],
  ["Jayashree Jayanth","jayashree.jayanth@ushur.com","Human Resources Director","Ushur"],
  ["Vani Sathvik","vani.sathvik@ekaplus.com","Vice President - HR & Admin","Eka Software Solutions"],
  ["Karthick Rengasamy","karthick@ideas2it.com","Head of Talent Acquisition","Ideas2IT Technologies"],
  ["Bhargavi Challa","bhargavic@aissel.com","Head of Human Resources","Aissel Technologies"],
  ["Viju Gangadharan","viju@imocha.io","Director - Human Resources","iMocha"],
  ["Benoy Koshy","benoy.koshy@sisainfosec.com","Head of Talent Acquisition","SISA"],
  ["Sitaram Kuraganti","sitaram.kuraganti@quadrantresource.com","Director Human Resources & TA","Quadrant Resource"],
  ["Fazal Karim","fazal.karim@metaoption.com","Staffing Head","MetaOption LLC"],
  ["Nirupama Sidhair","sidhair.n@qwikcilver.com","Head HR","Qwikcilver Solutions"],
  ["Rishabh","rishabhkalra@cetpainfotech.com","Recruitments Delivery Head","CETPA Infotech"],
  ["Jairaj Jagtap","jairaj.jagtap@satincorp.com","Delivery Head- Recruitment/ Recruiting Head","SA Technologies"],
  ["Nitya K","nk@ezesoft.com","Director, Human Resources","Eze Software"],
  ["Shubhada Kale","shubhada.kale@atyeti.com","Head of HR - India","Atyeti Inc"],
  ["Subramanian Adaikalam","subramanian.adaikalam@sumtotalsystems.com","Director, Global Talent Management","SumTotal Systems"],
  ["Debdutta Bhowmick","debdutta.bhowmick@atidiv.com","Human Resources Director","Atidiv"],
  ["Navneet Murthy","navneet@inventechinfo.com","Global Head - Recruitment","Inventech Info Solutions"],
  ["Robin B","robin@burgeonits.net","Director Of Recruiting","BURGEON IT SERVICES"],
  ["Swati M","swati_m@elsner.com","Head of HR","Elsner Technologies"],
  ["Gazal Singhania","gazal@imarkinfotech.com","Head Of Human Resources","iMark Infotech"],
  ["Shivani Chaturvedi","shivani.chaturvedi@mjunction.in","Chief People Officer","mjunction services"],
  ["Karthikeyan P","karthikeyan.p@hiverhq.com","Head of Talent Acquisition","Hiver"],
  ["Kinjal Shah","kinjal@biztechconsultancy.com","Human Resource Director","BiztechCS"],
  ["Timcy Bansal","timcy@exlyapp.com","Head of Talent Acquisition","Exly"],
  ["Pradeep Kumar","pradeep.kumar@andortech.com","Director - HR","Andor Tech"],
  ["Rehan Abdi","rehan.abdi@kiwitech.com","Head of Talent Acquisition","KiwiTech"],
  ["Archana Anand","archana@aufait.in","Head of Talent Acquisition","Aufait Technologies"],
  ["Swathi Narayan","snarayan@atsg.net","Head of Human Resources, India","ATSG"],
  ["Shefali Lal","shefali@dynproindia.com","Vice President Human Resources","DynPro"],
  ["Suruchi Khosla","skhosla@contecglobal.com","Head - HR(India)","Contec Global"],
  ["Raveendra Datla","raveendra.datla@smartims.com","Head of Talent Acquisition","Smart IMS"],
  ["Giri Babu","giri.babu@neovatic.com","Head of Human Resources","Neovatic Technologies"],
  ["Shivam Shukla","sshukla@tanishasystems.com","Director- Talent Acquisition","Tanisha Systems"],
  ["Sucheta Ukidwe","sucheta.ukidwe@mindstix.com","Director, HR","Mindstix Software Labs"],
  ["Priya Nair","priya@congruentindia.com","Director - Human Resources","Congruent Info-Tech"],
  ["Ramani Ganesh","ramani@hirect.in","Senior Vice President -Human Resources","Hirect India"],
  ["Livin Varghese","livin.varghese@teqfocus.com","Chief People Officer","Teqfocus"],
  ["Nupur Jain","nupur@ixigo.com","VP of Human Resources","ixigo"],
  ["Sreeja Sreedharan","sreeja.sreedharan@skuad.io","Director - Global Payroll","Skuad"],
  ["Ankita Rajrishi","ankita.rajrishi@condecsoftware.com","Head of Shared Services - Talent Acquisition","Condeco"],
  ["Nirupa Leeladhar","nirupa.l@smartt.com","SVP - HR & Talents","Smartt Digital Consulting"],
  ["Rohini Wwagh","rohiniw@nitinfotech.com","Vice President & Head HR","Nitor Infotech"],
  ["Nidhi","nidhi@empirix.com","Director of Human Resources Americas","Hammer"],
  ["Deepak Pawar","deepak.pawar@accutech.co.in","Head Of Human Resources","Accutech Power Solutions"],
  ["Tanul Jain","tanul.jain@cyware.com","Head of Talent Acquisition","Cyware"],
  ["Ashish Karnik","ashish.karnik@pavilion.io","Head Performance Engineering","Pavilion"],
  ["Monica Kamal","monica@pingasolutions.com","Director & HR Head","Pinga Solution"],
  ["Nirzari Sen","nirzari.sen@benisontech.com","Head of HR","Benison Technologies"],
  ["Chandan Thakur","chandan.kashyap@mysenseinc.com","Chief Talent Officer","Mysense Technologies"],
  ["Img Infotech","img@imgglobalinfotech.com","Head Of Human Resources","IMG Global Infotech"],
  ["Amiteesh Verma","amiteesh.verma@cheersin.com","Associate Director, Talent Acquisition","Cheers Interactive"],
  ["Poornima Gowda","poornima.gowda@fortanix.com","Head Of Human Resources","Fortanix"],
  ["Vivek Gaur","vivek@pacificbpo.com","Chief Peoples Officer","Pacific Global"],
  ["Sridhar Barige","sridhar.barioge@qwin.in","Head HR","JSoft Solutions"],
  ["Sunil Sarangdhar","sunil2@innov.in","AVP - HR","Innovsource"],
  ["Rajesh Balasubramanian","rajeshb@coffeebeans.io","Head of Talent Acquisition","CoffeeBeans Consulting"],
  ["Sacchin Kalkal","sacchin.kalkal@vertexglobalsolutions.com","VP- Talent Acquisition","Vertex Global Services"],
  ["Divya Keshav","divya.k@diamondpick.com","Head of Human Resources Operations","Diamondpick"],
  ["Divya P","divyap@cegonsoft.com","HR Head","Cegonsoft"],
  ["Anirban Ghoash","aghosh@trimaxamericas.com","Head - Human Resources Management","Data Glove"],
  ["Prem Nair","prem.nair@toshiba-tsip.com","Senior Director Human Resources","Toshiba Software (India)"],
  ["Nishita Algubelli","nishita@fissionlabs.com","HR & TA Director","Fission Labs"],
  ["Sujeet Rao","sujeet.rao@diverselynx.com","AVP-HR","Diverse Lynx"],
  ["Bindu Krishnan","bindu.krishnan@ospyn.com","Director-Human Resources and Administration","Ospyn Technologies"],
  ["Arpana Jaiswal","arpana@umbrella.inforcare.com","Head Human Resources","Umbrella Infocare"],
  ["Prastily Kp","prastily@vaisevika.in","Director -HR, Operations, Finance, Immigration","Vaisevika Consulting"],
  ["Supriya Dodia","hr.mumbai@konverge.co.in","Assistant Vice President -HR","Konverge Technologies"],
  ["Anand Thiagarajan","athiagrajan@inniveinc.com","Vice President - Human Resources","Innive Inc"],
  ["Saravana Kumar","saravanakumar@coresolutionsinc.com","Head - Human Resource and Administration","Core Solutions"],
  ["Veena Bhagwat","veena.bhagwat@infrabeat.com","Head - Human Resource","InfraBeat Technologies"],
  ["Sreeram Kaviliga","sreeram@qualizeal.com","Head Of Human Resources","QualiZeal"],
  ["Priyanka Grover","priyanka.grover@fifthnote.co","Function Head - Culture Building","fifthnote"],
  ["Karthik R","karthik@operative.com","Head of HR, India (Managed Services)","Operative"],
  ["Meghana Sarwate","megh@godrej.com","Head - Human Resource Information Systems","Godrej Infotech"],
  ["Shruti Gandhi","shruti.gandhi@moolya.com","Head of Human Resources Operations","Moolya"],
  ["Sathyanarayanan Reddipalli","sathyanarayanan@c1exchange.com","Head HR & Talent Acquisition","C1X"],
  ["Divya Jethi","divya.jethi@cloudanalogy.com","Sr. HR Head","Cloud Analogy"],
  ["Madhavee Singh","madhavee.singh@blucognition.com","Human Resource Manager- Head","bluCognition"],
  ["Pooja Malawade","pooja.malawade@nanostuffs.com","Head of HR","Nanostuffs"],
  ["Pradeep K","pradeep.k@mindtick.com","Vice President Human Resources","Mindtick India"],
  ["Gurpreet Jaggi","gurpreet.jaggi@betsol.com","Director, HR and Operations","BETSOL"],
  ["Narendra S","narendra.s@neterich.com","Director Talent Acquisition","Netenrich"],
  ["Sharwari Shah","sharwari.shah@revqurus.com","Director Human Resources","RevQurus Inc"],
  ["Ranjani Sunkara","ranjani.sunkara@incture.com","Associate Director Human Resources","Incture"],
  ["Kausar Khatri","kausar.khatri@lauren.co.in","Vice President Human Resources","Lauren Information Technologies"],
  ["Tess Burlow","tess.burlow@symbiosistech.com","Head of HR","Symbiosis Technologies"],
  ["Prakash Rc","prakash.rc@kriyanxw.com","Head Of Human Resources","Kriya IT"],
  ["Nitin Suri","nitin.suri@webomaze.com","Vice President Human Resources","Webomaze Technologies"],
  ["Rustom Taraporevala","rustom@1eq.com","Director Talent Management","eQ Technologic"],
  ["Pranitha Penmetsa","pranitha@tyfone.com","VP - Human Resources and Operations","tyfone"],
  ["Prashathi Pritiprada","prashathi@muvi.com","Director of HR","Muvi.com"],
  ["Suraj Shylaja","suraj.shylaja@q10x.com","Head Human Resources","_G10X"],
  ["Gautam Natuvial","gautam.n@tbloclks.com","Head of Talent Acquisition","TechBlocks"],
  ["Ravitha Devasenapathy","ravitha.devasenapathy@epikindifi.com","Chief Human Resources Officer","EPIKInDiFi"],
  ["Saurabh Lion","slion@curologic.com","Director - Talent Management","iauro Systems"],
  ["Raman Tak","rtsk@seec.com","HR-Director","SEEC"],
  ["Annapurna A","annapurna.a@fime.com","Head of HR & Admn","FIME"],
  ["Deepika Pandita","deepika@appinessworld.com","Head Of Human Resources","Appiness Interactive"],
  ["Charan Singh","charan@srinipharma.com","HEAD /HR","SRINI PHARMACEUTICALS"],
  ["Sarath V","sarath.v@insemitech.com","Associate Director-HR","Insemi Technology Services"],
  ["Sarieka Vaze","svaze@montran.com","Head - Human Resources & Administration","Montran Corporation"],
  ["Rakesh Arora","rakesh.arora@skilrock.com","Group CHRO","Skilrock Technologies"],
  ["Roy Mathew","roy.mathew@aqmtechnologies.com","AQM Group Talent Head","AQM Technologies"],
  ["Vishnu Ramesh","vishnu.vardhan@bitlasoft.com","Head of HR","Bitla Software"],
  ["Tara Cherian","tara.cherian@reflectioninfosystems.com","HR Director","Reflections Info Systems"],
  ["Gayatri Shanker","gayatri@dyooti.com","Head, Communications, People & Culture","Dyooti"],
  ["Anita Sidhwani","asidhwani@cleo.com","Head HR","Cleo"],
  ["Nitin Padharia","nitin.padharia@indianic.com","Head - HR / ILDC and L&D","IndiaNIC Infotech"],
];

if (records.length !== 150) throw new Error(`Expected 150 screenshot rows, found ${records.length}`);
const appended = records.map((row, i) => [201 + i, ...row]);
table.rows.add(null, appended);

const finalRow = 194 + appended.length;
sheet.getRange(`A1:E${finalRow}`).format.autofitRows();
const emailValues = sheet.getRange(`C2:C${finalRow}`).values.map(r => String(r[0] ?? "").trim().toLowerCase());
const emailCounts = new Map();
for (const email of emailValues) if (email) emailCounts.set(email, (emailCounts.get(email) ?? 0) + 1);
const duplicateEmails = [...emailCounts.entries()].filter(([, count]) => count > 1);
for (let i = 0; i < emailValues.length; i++) {
  if (emailValues[i] && emailCounts.get(emailValues[i]) > 1) {
    sheet.getRange(`C${i + 2}`).format.fill = "#FFF2CC";
  }
}

workbook.recalculate();
const summary = await workbook.inspect({kind:"region",sheetId:"HR Contacts",range:`A1:E${finalRow}`,maxChars:700,tableMaxRows:3,tableMaxCols:5});
console.log(summary.ndjson);
const tail = await workbook.inspect({kind:"region",sheetId:"HR Contacts",range:`A${finalRow - 4}:E${finalRow}`,maxChars:1200,tableMaxRows:6,tableMaxCols:5});
console.log(tail.ndjson);
console.log(JSON.stringify({appended:appended.length,totalContacts:emailValues.length,uniqueEmails:emailCounts.size,duplicateEmails}));

const reviewPreview = await workbook.render({sheetName:"HR Contacts",range:"A160:E199",scale:1,format:"png"});
await fs.writeFile(`${outputDir}/preview_duplicate_review.png`,new Uint8Array(await reviewPreview.arrayBuffer()));
const endPreview = await workbook.render({sheetName:"HR Contacts",range:`A${finalRow - 12}:E${finalRow}`,scale:1,format:"png"});
await fs.writeFile(`${outputDir}/preview_latest_end.png`,new Uint8Array(await endPreview.arrayBuffer()));

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(filePath);
console.log(`Appended source rows 201-350 to workbook.`);
