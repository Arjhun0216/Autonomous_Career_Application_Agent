from pathlib import Path
import zipfile, textwrap
from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.style import WD_STYLE_TYPE

ROOT=Path(r'C:\Users\ARJUN\Downloads\Projects\job-platform')
ZIP=Path(r'C:\Users\ARJUN\Music\New folder.zip')
OUT=ROOT/'output'/'documents'/'AI_Career_Assistant_Project_Report.docx'
ASSET=OUT.parent/'_report_assets'; ASSET.mkdir(parents=True,exist_ok=True); OUT.parent.mkdir(parents=True,exist_ok=True)
NAVY='#183153'; BLUE='#2F67A5'; MUTED='#536273'; INK='#202A36'
def ff(b=False,m=False): return r'C:\Windows\Fonts\consola.ttf' if m else (r'C:\Windows\Fonts\arialbd.ttf' if b else r'C:\Windows\Fonts\arial.ttf')
def box(d,xy,title,detail='',fill='#F7FAFD'):
    d.rounded_rectangle(xy,radius=16,fill=fill,outline=BLUE,width=4); x1,y1,x2,y2=xy
    d.text(((x1+x2)//2,y1+23),title,font=ImageFont.truetype(ff(True),29),fill=NAVY,anchor='ma')
    if detail:
        for i,line in enumerate(textwrap.wrap(detail,35)[:3]): d.text(((x1+x2)//2,y1+64+i*28),line,font=ImageFont.truetype(ff(),21),fill=MUTED,anchor='ma')
def ar(d,a,b):
    import math
    d.line([a,b],fill=BLUE,width=5); t=math.atan2(b[1]-a[1],b[0]-a[0]); L=18
    d.polygon([b,(b[0]-L*math.cos(t-.45),b[1]-L*math.sin(t-.45)),(b[0]-L*math.cos(t+.45),b[1]-L*math.sin(t+.45))],fill=BLUE)
def fig_arch(p):
    im=Image.new('RGB',(1700,900),'white'); d=ImageDraw.Draw(im)
    d.text((850,35),'AI CAREER PLATFORM - SYSTEM ARCHITECTURE',font=ImageFont.truetype(ff(True),38),fill=NAVY,anchor='ma')
    coords=[(50,320,340,490,'Candidate','React + Vite client'),(430,180,800,340,'REST backend','Spring Boot controllers'),(430,490,800,650,'Persistence','MySQL / MariaDB'),(930,110,1290,270,'Career services','Profile, jobs, applications'),(930,350,1290,510,'AI services','Embedding service, Gemini'),(930,610,1290,770,'Integrations','Gmail OAuth, ATS browser flows'),(1400,300,1650,470,'Files','Resumes, letters, brochure')]
    for x1,y1,x2,y2,t,s in coords: box(d,(x1,y1,x2,y2),t,s)
    for a,b in [((340,405),(430,260)),((340,405),(430,560)),((800,260),(930,190)),((800,280),(930,430)),((800,300),(930,690)),((800,560),(930,240)),((1290,700),(1400,390))]: ar(d,a,b)
    d.text((850,850),'Logical view derived from the supplied code and schema',font=ImageFont.truetype(ff(),20),fill=MUTED,anchor='mm'); im.save(p)
def fig_flow(p):
    im=Image.new('RGB',(1700,860),'white'); d=ImageDraw.Draw(im)
    d.text((850,35),'CANDIDATE APPLICATION WORKFLOW',font=ImageFont.truetype(ff(True),38),fill=NAVY,anchor='ma')
    xs=[40,370,700,1030,1360]; labels=[('Profile','skills, experience'),('Discover','search / recommend'),('Prepare','job-specific resume'),('Track','create application'),('Employer ATS','supported adapter')]
    for x,(t,s) in zip(xs,labels): box(d,(x,180,x+280,340),t,s)
    for x in xs[:-1]: ar(d,(x+280,260),(x+330,260))
    box(d,(1030,540,1310,700),'Manual handoff','login, CAPTCHA, review',fill='#FFF8E7'); ar(d,(1500,340),(1170,540))
    box(d,(370,540,700,700),'Answer questions','pending user input'); ar(d,(1030,620),(700,620)); ar(d,(700,620),(1030,620))
    d.text((850,800),'Protected employer steps remain under candidate control.',font=ImageFont.truetype(ff(),20),fill=MUTED,anchor='mm'); im.save(p)
def fig_erd(p):
    im=Image.new('RGB',(1700,900),'white'); d=ImageDraw.Draw(im)
    d.text((850,35),'LOGICAL DATA MODEL - job_platform.sql',font=ImageFont.truetype(ff(True),37),fill=NAVY,anchor='ma')
    def ent(x,y,w,title,fields):
        h=60+len(fields)*34; d.rounded_rectangle((x,y,x+w,y+h),radius=12,fill='#F7FAFD',outline=BLUE,width=3); d.rectangle((x,y,x+w,y+52),fill=NAVY)
        d.text((x+16,y+26),title,font=ImageFont.truetype(ff(True),24),fill='white',anchor='lm')
        for i,f in enumerate(fields): d.text((x+15,y+74+i*34),f,font=ImageFont.truetype(ff(),19),fill=INK,anchor='lm')
    ent(50,170,440,'users',['PK user_id','email, name, phone, location','skills, education, experience, projects'])
    ent(620,150,450,'job_applications',['PK application_id','user_id -> users','job_id -> jobs','status, resume_path, applied_at'])
    ent(1220,150,430,'application_questions',['PK question_id','application_id -> applications','question, final_answer, status'])
    ent(620,570,450,'jobs',['PK job_id','company, title, location','jd, skills, source, embedding'])
    ent(50,590,440,'hr_contacts',['PK hr_id','company_name, mail_id'])
    ar(d,(490,280),(620,260)); ar(d,(1070,260),(1220,260)); ar(d,(845,570),(845,490))
    d.text((850,840),'Arrows show logical ID references; see schema notes for constraint status.',font=ImageFont.truetype(ff(),20),fill=MUTED,anchor='mm'); im.save(p)

arch=ASSET/'architecture.png'; flow=ASSET/'workflow.png'; erd=ASSET/'erd.png'; fig_arch(arch); fig_flow(flow); fig_erd(erd)
doc=Document(); sec=doc.sections[0]
sec.top_margin=Inches(.78); sec.bottom_margin=Inches(.72); sec.left_margin=Inches(1); sec.right_margin=Inches(.85); sec.footer_distance=Inches(.38); sec.different_first_page_header_footer=True
normal=doc.styles['Normal']; normal.font.name='Times New Roman'; normal.font.size=Pt(11); normal.font.color.rgb=RGBColor(32,42,54); normal.paragraph_format.line_spacing=1.12; normal.paragraph_format.space_after=Pt(6)
for sn,sz in [('Title',24),('Heading 1',16),('Heading 2',13),('Heading 3',11.5)]:
 s=doc.styles[sn]; s.font.name='Times New Roman'; s.font.size=Pt(sz); s.font.bold=True; s.font.color.rgb=RGBColor(0,0,0); s.paragraph_format.keep_with_next=True; s.paragraph_format.space_before=Pt(9); s.paragraph_format.space_after=Pt(5)
code=doc.styles.add_style('Code Listing',WD_STYLE_TYPE.PARAGRAPH); code.font.name='Consolas'; code.font.size=Pt(7.1); code.paragraph_format.line_spacing=Pt(8.1); code.paragraph_format.space_after=Pt(0)
f=sec.footer.paragraphs[0]; f.alignment=WD_ALIGN_PARAGRAPH.CENTER; rr=f.add_run('AI Career Assistant Project Report  |  '); rr.font.size=Pt(8); rr.font.color.rgb=RGBColor(100,110,120); fld=OxmlElement('w:fldSimple'); fld.set(qn('w:instr'),'PAGE'); f._p.append(fld)
def H(t,l=1): return doc.add_paragraph(t,style=f'Heading {l}')
def P(t): return doc.add_paragraph(t)
def B(t): doc.add_paragraph(t,style='List Bullet')
def borderless(t):
 pr=t._tbl.tblPr; bs=OxmlElement('w:tblBorders')
 for e in ('top','left','bottom','right','insideH','insideV'):
  x=OxmlElement('w:'+e); x.set(qn('w:val'),'nil'); bs.append(x)
 pr.append(bs)
def table(cap,heads,rows,widths):
 t=doc.add_table(rows=1,cols=len(heads)); t.alignment=WD_TABLE_ALIGNMENT.CENTER; t.autofit=False; borderless(t)
 for i,h in enumerate(heads):
  c=t.rows[0].cells[i]; c.text=h; c.width=Inches(widths[i]); c.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
  for r in c.paragraphs[0].runs:r.bold=True;r.font.size=Pt(9);r.font.color.rgb=RGBColor(24,49,83)
 for j,row in enumerate(rows):
  cells=t.add_row().cells
  for i,v in enumerate(row):
   c=cells[i]; c.text=str(v); c.width=Inches(widths[i]); c.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
   if j%2: sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'F2F6FA');c._tc.get_or_add_tcPr().append(sh)
   for pp in c.paragraphs:
    pp.paragraph_format.space_after=Pt(2);pp.paragraph_format.line_spacing=1.0
    for r in pp.runs:r.font.size=Pt(8.4)
 p=doc.add_paragraph(cap);p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.space_after=Pt(8)
 for r in p.runs:r.italic=True;r.font.size=Pt(9);r.font.color.rgb=RGBColor(83,98,115)
def picture(path,cap):
 p=doc.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.keep_with_next=True;p.add_run().add_picture(str(path),width=Inches(6.2))
 c=doc.add_paragraph(cap);c.alignment=WD_ALIGN_PARAGRAPH.CENTER;c.paragraph_format.keep_together=True
 for r in c.runs:r.italic=True;r.font.size=Pt(9);r.font.color.rgb=RGBColor(83,98,115)
def toc(text):
 p=doc.add_paragraph();p.paragraph_format.space_after=Pt(2);p.paragraph_format.tab_stops.add_tab_stop(Inches(6.25),WD_TAB_ALIGNMENT.RIGHT,WD_TAB_LEADER.DOTS);p.add_run(text+'\t00');return p

# Cover and front matter
doc.add_paragraph().paragraph_format.space_after=Pt(58)
for txt,size,col in [('AI CAREER ASSISTANT',25,NAVY),('JOB DISCOVERY AND APPLICATION PLATFORM',19,NAVY),('A PROJECT REPORT',14,INK)]:
 p=doc.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;r=p.add_run(txt);r.bold=True;r.font.size=Pt(size);r.font.color.rgb=RGBColor.from_string(col.lstrip('#'))
doc.add_paragraph().paragraph_format.space_after=Pt(45)
for txt in ['Submitted in partial fulfillment of the requirements for the project','Department of Computer Science and Engineering','Government College of Engineering, Bargur','October 2026']:
 p=doc.add_paragraph(txt);p.alignment=WD_ALIGN_PARAGRAPH.CENTER
doc.add_page_break()
H('ABSTRACT');P('Job seekers often move between job boards, profile forms, resume editors, employer portals and email workflows. This project presents an AI-assisted career platform that brings these activities into one workspace. The platform stores a candidate profile, retrieves job records, presents profile-aware recommendations, highlights skill gaps and generates role-oriented application materials. It tracks applications and supports employer-specific browser workflows, while leaving authentication challenges and other required human actions to the candidate. A college mailing feature prepares a reusable outreach draft and sends it with a brochure to stored HR contacts.')
P('The implementation uses a React and Vite client, a Spring Boot REST backend, MySQL, a Python embedding service, Playwright-based browser automation, Gemini for selected language-model tasks and Gmail OAuth for authorized mail delivery. The supplied database dump contains five principal tables: users, jobs, job_applications, application_questions and hr_contacts. This report documents architecture, workflow, data model, APIs and implementation. It does not claim measured hiring outcomes or production-scale performance; those require a separate evaluation.')
P('Keywords: career platform, job recommendation, resume generation, application tracking, browser automation, skill matching.')
doc.add_page_break()
H('TABLE OF CONTENTS')
TOC=['ABSTRACT','LIST OF FIGURES','LIST OF TABLES','LIST OF ABBREVIATIONS','SUSTAINABLE DEVELOPMENT GOAL CONTRIBUTIONS','CHAPTER 1  INTRODUCTION','1.1  Project context','1.2  Problem statement','1.3  Objectives and scope','CHAPTER 2  LITERATURE REVIEW','2.1  Resume and job matching','2.2  Personalized resume generation','2.3  Fairness and limitations','2.4  Research gap','CHAPTER 3  PROPOSED PLATFORM','3.1  System overview','3.2  Core features','3.3  Application workflow','CHAPTER 4  SYSTEM DESIGN','4.1  Architecture','4.2  Database design','4.3  API design','4.4  Security and data handling','CHAPTER 5  IMPLEMENTATION','5.1  Technology stack','5.2  Backend and frontend','5.3  Recommendation and resume services','5.4  Application automation and mailing','CHAPTER 6  SYSTEM OUTCOMES AND DISCUSSION','6.1  Implemented outcomes','6.2  Limitations and risks','CHAPTER 7  CONCLUSION AND FUTURE WORK','7.1  Conclusion','7.2  Future work','APPENDIX 1  API ENDPOINT REFERENCE','APPENDIX 2  SAMPLE CODE','REFERENCES']
for t in TOC:toc(t)
doc.add_page_break();H('LIST OF FIGURES')
for t in ['Figure 3.1  Candidate application workflow','Figure 4.1  AI career platform system architecture','Figure 4.2  Logical data model - job_platform.sql']:toc(t)
doc.add_page_break();H('LIST OF TABLES')
for t in ['Table 3.1  Project objectives and platform response','Table 4.1  Database entities and responsibilities','Table 4.2  Important schema fields','Table 5.1  Technology stack','Table 6.1  Implemented outcomes and evidence','Table A1.1  REST API endpoint reference']:toc(t)
doc.add_page_break();H('LIST OF ABBREVIATIONS')
for a,b in [('AI','Artificial Intelligence'),('API','Application Programming Interface'),('ATS','Applicant Tracking System'),('CRUD','Create, Read, Update and Delete'),('CSS','Cascading Style Sheets'),('HTTP','Hypertext Transfer Protocol'),('JSON','JavaScript Object Notation'),('JWT','JSON Web Token'),('LLM','Large Language Model'),('OAuth','Open Authorization'),('REST','Representational State Transfer'),('SDG','Sustainable Development Goal'),('SQL','Structured Query Language'),('UI','User Interface'),('URL','Uniform Resource Locator')]:
 p=doc.add_paragraph();p.paragraph_format.left_indent=Inches(.55);p.paragraph_format.tab_stops.add_tab_stop(Inches(1.35));p.add_run(a).bold=True;p.add_run('\t'+b)
doc.add_page_break();H('SUSTAINABLE DEVELOPMENT GOAL CONTRIBUTIONS')
P('The platform is aligned with employment access, skills development and inclusive digital services. These points describe potential contributions enabled by project features; they are not measured impacts or formal SDG certification. Goal titles follow the United Nations Sustainable Development Goals [1].')
for t,d in [('SDG 1 - No Poverty','Job discovery and application tools may help users access employment opportunities; the project has not measured income outcomes.'),('SDG 4 - Quality Education','Skill recommendations and learning-resource links can support continued learning.'),('SDG 5 - Gender Equality','The service should offer the same access regardless of gender; fairness needs explicit evaluation.'),('SDG 8 - Decent Work and Economic Growth','The core purpose is helping candidates find suitable roles and manage job applications.'),('SDG 9 - Industry, Innovation and Infrastructure','The project integrates recommendation, language-model and workflow automation components.'),('SDG 10 - Reduced Inequalities','Search and recommendation features can broaden opportunity discovery across locations and experience levels.'),('SDG 11 - Sustainable Cities and Communities','Location-aware and remote-job discovery can broaden access beyond one local area.'),('SDG 12 - Responsible Consumption and Production','Digital profiles and online application materials can reduce paper-based handling.'),('SDG 16 - Peace, Justice and Strong Institutions','Application tracking and human review can improve process transparency; privacy remains essential.'),('SDG 17 - Partnerships for the Goals','College outreach connects the institution with recruiting contacts through structured mailing.')]:
 p=doc.add_paragraph();p.paragraph_format.space_after=Pt(5);p.paragraph_format.keep_together=True;r=p.add_run(t+'  ');r.bold=True;r.font.color.rgb=RGBColor(24,49,83);p.add_run(d)
doc.add_page_break()

# Chapters
H('CHAPTER 1  INTRODUCTION');H('1.1  Project context',2);P('Job seekers manage listings, profile information, resume versions, employer forms and follow-ups across separate websites. Re-entering profile details creates effort, while role-specific requirements and different ATS interfaces complicate applications. The AI Career Assistant provides a common workspace for candidate profile management, job discovery, application materials and status tracking, with user review for sensitive details and employer-controlled steps.')
H('1.2  Problem statement',2);P('Candidates need a unified way to organize career information, connect job requirements with confirmed skills, prepare consistent application materials and track employer responses. Automation must reduce repeated work without pretending to submit an application when sign-in, CAPTCHA or candidate approval is still required.')
H('1.3  Objectives and scope',2)
for x in ['Maintain reusable identity, education, experience, skill, project and work-authorization details.','Retrieve job information and provide profile-aware job and skill recommendations.','Generate role-oriented resumes and cover letters using candidate-confirmed facts.','Track applications and retain employer questions and user answers.','Support company workflows with clear user handoffs for protected actions.','Provide college HR outreach using a reusable draft and brochure.']:B(x)
table('Table 3.1 Project objectives and platform response',['Objective','Platform response'],[('Reduce repeated data entry','Reuse the candidate profile across job and application workflows.'),('Improve job discovery','Return recommendations using job and profile information.'),('Support tailored materials','Combine role context with candidate-provided career data.'),('Track application progress','Persist applications, documents, statuses, questions and errors.'),('Preserve candidate control','Pause for sign-in, CAPTCHA, review and unknown answers.')],[2,4.2])
H('1.4  Scope and constraints',2);P('The supplied source and SQL support profile management, job retrieval, recommendations, application records, resume and cover-letter generation, skill confirmation, employer workflows, Gmail connection and college mailing. The database dump is a development snapshot and includes job records and embeddings. The project does not establish that all ATS workflows remain stable, that candidates receive interviews, or that recommendations are unbiased. Browser automation must respect employer security controls and terms of use.')
H('1.5  Report organization',2);P('Chapter 2 reviews related research. Chapter 3 describes the proposed platform and user workflow. Chapter 4 covers architecture, database, APIs and security. Chapter 5 describes implementation. Chapter 6 summarizes outcomes and limitations. Chapter 7 concludes and identifies future work. Appendix 1 lists API routes; Appendix 2 contains source excerpts.')
doc.add_page_break();H('CHAPTER 2  LITERATURE REVIEW')
H('2.1  Resume and job matching',2);P('Yu, Zhang and Yu introduced ConFit, combining data augmentation and contrastive learning to improve resume-job ranking under sparse interaction data [2]. This supports semantic representations for matching but also shows that comparisons depend on dataset and evaluation choices. The supplied project includes job/profile representations and an embedding service, but it does not include a published offline benchmark; therefore, its recommendation quality should not be compared numerically with ConFit.')
P('A recommendation score is not a hiring decision. Candidates need to inspect evidence such as relevant skills, experience and location, and confirm that a job posting is current.')
H('2.2  Personalized resume generation',2);P('ResumeFlow describes a pipeline that extracts role details from a job description and a user resume, then uses an LLM to refine a role-specific resume [3]. This motivates combining a reusable profile with job context. The project contains a resume prompt builder and LaTeX compiler. Generation must preserve factual boundaries: the model may organize and emphasize candidate-provided experience, but should not claim unconfirmed skills or achievements.')
P('Gan, Zhang and Mori studied an LLM-agent framework for recruitment resume screening [4]. Their work illustrates possible automation benefits while leaving human oversight important. In this project, generated materials remain candidate-controlled drafts; employer decisions are outside scope.')
H('2.3  Fairness and limitations',2);P('Vaishampayan et al. compared human and LLM resume ratings on real applications and found that model scores were not interchangeable with human ratings [5]. Nghiem et al. investigated name-based bias in LLM employment recommendations [6]. These studies support transparent recommendations, careful data minimization, fairness evaluation and human review rather than treating a match score as a definitive measure of candidate quality.')
H('2.4  Research gap',2);P('The reviewed work focuses on matching models, resume generation or screening. This project addresses an integration gap by placing profile management, recommendations, resume generation, application tracking, ATS workflows, user-answer collection and college outreach in one workspace. Integration is a software contribution, not evidence of improved employment outcomes. Evaluation should measure relevance, factuality, accessibility, task completion and fairness.')
H('2.5  Summary',2);P('Prior research provides methods for matching and warns that automated scores can diverge from human judgment or exhibit bias. The project combines semantic matching and language-model assistance in a broader workflow. Candidate control, truthful profile data and measurable evaluation remain essential.')
doc.add_page_break();H('CHAPTER 3  PROPOSED PLATFORM')
H('3.1  System overview',2);P('After authentication, a candidate maintains a profile, views jobs, checks recommendation context and chooses whether to apply. The backend creates application records, prepares job-specific materials, stores employer questions and updates status from observed workflow outcomes. Optional modules support learning resources, Gmail delivery and employer-specific interactions.')
H('3.2  Core features',2)
for x in ['Profile management with contact, education, work, skills, projects and authorization details.','Job detail retrieval and profile-aware job/skill recommendations.','Resume and cover-letter generation with role context.','Application creation, status lists, pending questions and stored answers.','ATS-specific navigation, supported form completion and manual handoff.','College HR mailing with a reusable email draft and brochure.']:B(x)
picture(flow,'Figure 3.1 Candidate application workflow')
H('3.3  Workflow and requirements',2);P('The workflow begins with a completed profile. The candidate reviews job details and recommendation context, then initiates an application. Unknown form answers remain pending for user input. Sign-in, CAPTCHA and other protected steps are completed by the candidate on the employer page. A success status is recorded only when the workflow observes a clear success state; otherwise an error or manual-action state is retained.')
P('Functional requirements include authenticated profile read/update, job details, recommendations, application creation and tracking, saved answers, resume generation, skill confirmation and HR outreach. Quality requirements include secure token handling, accurate status, readable generated documents, bounded automation, clear errors and per-user data isolation.')
doc.add_page_break();H('CHAPTER 4  SYSTEM DESIGN')
H('4.1  Architecture',2);P('The React client communicates with Spring Boot REST controllers. Authentication identifies the current user. Services apply business rules and repositories access MySQL. Recommendation requests can use a separate embedding service. Resume components call a language-model service and compile LaTeX. Playwright and company adapters handle supported ATS pages. Gmail OAuth authorizes email delivery; local storage holds generated documents and the college brochure.')
picture(arch,'Figure 4.1 AI career platform system architecture')
H('4.2  Database design',2);P('The supplied job_platform.sql defines five principal entities. Profile sections are held as long text in users. Job records contain description, skills, source and embedding. Applications keep user/job identifiers, company labels, file paths, status, email fields, timestamps and errors. Application questions retain the prompt, field metadata, options, temporary/final answer and status. HR contacts are stored separately.')
picture(erd,'Figure 4.2 Logical data model from the supplied schema')
table('Table 4.1 Database entities and responsibilities',['Entity','Primary key','Purpose'],[('users','user_id','Identity, authentication and candidate profile.'),('jobs','job_id','Employer, posting, role, location, skills and embedding.'),('job_applications','application_id','Candidate-job workflow, documents, status and timestamps.'),('application_questions','question_id','Employer questions and user responses.'),('hr_contacts','hr_id','Company HR email contacts.')],[1.5,1.2,3.5])
table('Table 4.2 Important schema fields',['Table','Fields'],[('users','email, first_name, phone, location, skills, education, experience, projects, auth_provider, gmail_connected'),('jobs','job_id, company, title, jd, skills, location, salary, job_url, source, posted_date, embedding'),('job_applications','user_id, job_id, company_name, status, resume_path, cover_letter_path, ready_at, error_message, applied_at'),('application_questions','application_id, question, field_id, final_answer, available_options, status'),('hr_contacts','company_name, mail_id')],[1.7,4.5])
H('4.3  API design',2);P('Routes are grouped under /api/auth, /api/profile, /api/jobs, /api/recommendations, /api/applications, /api/skills, /api/application-agent, /api/gmail and /api/college-mail. Authenticated endpoints use the current user context. Answer updates check that the application belongs to the user before persisting the response. Appendix 1 summarizes the controller routes.')
H('4.4  Security and data handling',2);P('The implementation uses token-based authentication and supports Google sign-in. OAuth tokens and personal profile information require protected storage. The supplied configuration contains a JWT secret value in the properties file; deployment should replace it with a secret manager value and rotate it if used. Application files, logs and SQL backups can contain personal data. Employer automation should not bypass sign-in or CAPTCHA; these remain candidate handoffs.')
P('The SQL declares primary keys for all five tables. Application, question, user and job identifiers act as logical links; verify and enforce foreign-key constraints in deployment migrations.')
doc.add_page_break();H('CHAPTER 5  IMPLEMENTATION')
table('Table 5.1 Technology stack',['Layer','Implementation observed'],[('Client','React, Vite, React Router and Axios services.'),('Backend','Java Spring Boot REST controllers, services and repositories.'),('Persistence','MySQL/MariaDB through Spring Data JPA/Hibernate.'),('Authentication','JWT sessions, Google OAuth2 and Gmail OAuth2.'),('Recommendation','Java services and a separate Python embedding service.'),('Resume','Gemini client, prompt builder, LaTeX compiler and generated files.'),('Browser automation','Playwright, ATS detection and company-specific workflows.'),('Files','Local uploads and generated-resumes directories.')],[1.6,4.6])
H('5.1  Frontend and backend',2);P('The React client has pages for profile completion, job details, pending approvals, applied jobs, skill recommendations and college mailing. Backend packages separate user, job, application, recommendation, resume, HR, Gmail, skill, security and college mail behavior. Controllers map HTTP requests to service operations and repositories manage persistence.')
H('5.2  Recommendation and resume',2);P('Recommendation routes return job and skill suggestions. Job records include an embedding field and configuration points to a local embedding service. Skill verification identifies job-specific skill gaps and stores candidate confirmation. Resume generation combines job and profile context, requests text from Gemini and compiles LaTeX. The output should be validated for factuality and successful compilation before download.')
H('5.3  Automation and mailing',2);P('Application endpoints create and list records, expose pending approvals, update question answers and support reapplication. The source includes workflows for Zoho, IBM, NVIDIA, Greenhouse, Lever, Keka and Workday. Since employer pages change and protected gates vary, the system must preserve manual-action states instead of claiming completion from a click. The college-mail controller exposes draft retrieval, brochure upload and send-to-all operations.')
H('5.4  Repository structure',2);P('Backend packages reside under com.jobscraper.job_platform; frontend code resides under job-platform-frontend/src. Resume outputs use generated-resumes and uploaded files use uploads. The supplied archive also contains a recommendation model and workflow tests. Appendix 2 includes representative excerpts from the archive.')
doc.add_page_break();H('CHAPTER 6  SYSTEM OUTCOMES AND DISCUSSION')
H('6.1  Implemented outcomes',2);P('The supplied source supports a connected career workflow: candidate profile, recommendations, skill guidance, role-oriented materials, application records, question handling, employer-specific workflows and college outreach. These are implementation outcomes. No production user totals, conversion rates or employment outcomes were supplied.')
table('Table 6.1 Implemented outcomes and evidence',['Outcome','Evidence in supplied project'],[('Candidate data reuse','User entity and profile endpoints store profile sections.'),('Job / skill recommendations','Recommendation controller exposes job and skill routes.'),('Role-oriented materials','Resume prompt builder, Gemini client and LaTeX compiler.'),('Application tracking','Application entity, status fields, question controller and frontend.'),('ATS interaction','Company workflow and form automation classes.'),('College outreach','College-mail controller, service and brochure configuration.')],[1.8,4.4])
H('6.2  Limitations and risks',2)
for x in ['Recommendation and extraction quality need evaluation on an approved labeled dataset.','Generated resumes require factuality review and must not add unconfirmed skills.','Employer pages may change; login and CAPTCHA require candidate completion.','Long-text profile fields complicate validation and structured reporting.','Local file storage and development configuration need production hardening.','No measured fairness, accessibility, latency, reliability or application-success rate was supplied.']:B(x)
H('6.3  Evaluation plan',2);P('Future evaluation should measure recommendation precision at a declared cutoff, skill extraction precision/recall, resume factuality, compilation success, time to complete supported workflows, handoff correctness and accessibility. Fairness evaluation should use an approved design and report sample size, period, inclusion criteria and uncertainty. No metric values are invented in this report.')
doc.add_page_break();H('CHAPTER 7  CONCLUSION AND FUTURE WORK')
H('7.1  Conclusion',2);P('The AI Career Assistant combines candidate profile management, job recommendations, skill guidance, resume generation, application tracking, employer-specific browser workflows and college outreach. The supplied Spring Boot, React, Python and MySQL artifacts show the implementation structure, while the SQL dump makes the core schema traceable. The platform is a candidate-side productivity assistant; it organizes information and automates supported steps while hiring decisions and protected employer actions remain under human control.')
H('7.2  Future work',2)
for x in ['Normalize profile sections into validated related tables and add schema migrations and foreign keys.','Add explainable match factors and candidate correction controls.','Evaluate generated resumes for factuality, layout and role relevance.','Improve ATS adapter recovery, selector versioning and manual handoff visibility.','Strengthen secret handling, encrypted token storage, retention and audit logging.','Conduct privacy-reviewed fairness, accessibility, reliability and performance studies.']:B(x)
doc.add_page_break();H('APPENDIX 1  API ENDPOINT REFERENCE')
P('The endpoint inventory is derived from Spring controllers in the supplied project. Authentication requirements should be confirmed against the deployment security policy. The resume extraction route is described by its controller; verify its exact path and multipart parameter in ResumeExtractionController before integrating an external client.')
api=[('POST','/api/auth/register','Create local account.'),('POST','/api/auth/login','Authenticate local account.'),('GET','/api/profile/me','Read current profile.'),('PUT','/api/profile/me','Update current profile.'),('POST','/api/profile/extract-resume','Extract profile data from uploaded resume; multipart.'),('GET','/api/jobs/{jobId}','Read job details.'),('GET','/api/recommendations','Get job recommendations.'),('GET','/api/recommendations/skills','Get skill recommendations.'),('POST','/api/applications','Create application record.'),('GET','/api/applications','List current user applications.'),('GET','/api/applications/pending','List pending items.'),('GET','/api/applications/{id}','Read one application.'),('GET','/api/applications/{id}/questions','List application questions.'),('PUT','/api/applications/{id}/questions/{questionId}','Save answer.'),('POST','/api/applications/{id}/approve-and-send','Approve and send email.'),('POST','/api/applications/{id}/reapply','Reapply.'),('GET','/api/skills/missing','Read missing profile skills.'),('GET','/api/skills/verify?jobId={id}','Verify job skills.'),('POST','/api/skills/confirm','Confirm a skill.'),('POST','/api/skills/learning-resources','Request learning resources.'),('POST','/api/application-agent/test/{id}','Inspect application page.'),('GET','/api/hr','List HR contacts.'),('GET','/api/hr/company/{name}','Find company contacts.'),('POST','/api/hr','Create HR contact.'),('GET','/api/gmail/connect-url','Begin Gmail OAuth.'),('GET','/api/gmail/status','Read Gmail status.'),('GET','/api/gmail/oauth2/callback','Complete OAuth callback.'),('GET','/api/college-mail/draft','Read college email draft.'),('POST','/api/college-mail/brochure','Upload brochure.'),('POST','/api/college-mail/send','Send to HR contacts.'),('POST','/api/projects/ats-questions','Request ATS questions.'),('GET','/api/resume/gemini-test','Resume diagnostic route.'),('GET','/api/resume/test','Resume diagnostic route.')]
table('Table A1.1 REST API endpoint reference',['Method','Route','Purpose'],api,[.75,3.0,2.45])
P('Company-specific workflows are invoked by application processing services rather than separate public endpoints. Application and question updates should enforce ownership by the authenticated user.')
doc.add_page_break();H('APPENDIX 2  SAMPLE CODE');P('The following pages present selected source excerpts from the supplied project archive. Paths are archive-relative. Excerpts illustrate the data model, profile, recommendation, ATS, email and resume modules.')

# Append ten one-page code samples, read directly from the uploaded archive.
samples=[('src/main/java/com/jobscraper/job_platform/user/User.java',1,48),('src/main/java/com/jobscraper/job_platform/job/Job.java',1,63),('src/main/java/com/jobscraper/job_platform/application/JobApplication.java',1,55),('src/main/java/com/jobscraper/job_platform/application/ApplicationQuestion.java',1,56),('src/main/java/com/jobscraper/job_platform/profile/ProfileController.java',1,62),('src/main/java/com/jobscraper/job_platform/application/JobApplicationController.java',1,62),('src/main/java/com/jobscraper/job_platform/recommendation/RecommendationController.java',1,49),('src/main/java/com/jobscraper/job_platform/application/automation/KekaWorkflow.java',1,65),('src/main/java/com/jobscraper/job_platform/college/CollegeMailController.java',1,60),('src/main/java/com/jobscraper/job_platform/resume/ResumePromptBuilder.java',1,50)]
with zipfile.ZipFile(ZIP) as z:
 for i,(rel,start,end) in enumerate(samples,1):
  doc.add_page_break(); h=H(f'Code sample {i:02d}',2); h.paragraph_format.space_after=Pt(2)
  p=doc.add_paragraph('Archive path: '+rel);p.paragraph_format.space_after=Pt(4)
  for r in p.runs:r.italic=True;r.font.size=Pt(8.5);r.font.color.rgb=RGBColor(83,98,115)
  src=z.read('New folder/'+rel).decode('utf-8',errors='replace').splitlines(); chosen=src[start-1:min(end,len(src))]
  code_text='\n'.join(f'{start+j:04d}  {line}' for j,line in enumerate(chosen))
  p=doc.add_paragraph(style='Code Listing');p.paragraph_format.left_indent=Inches(.08);p.add_run(code_text)

doc.add_page_break();H('REFERENCES')
refs=['[1] United Nations Department of Economic and Social Affairs, “The 17 Goals,” Sustainable Development Goals. https://sdgs.un.org/goals (accessed 26 October 2026).','[2] X. Yu, J. Zhang and Z. Yu, “ConFit: Improving Resume-Job Matching using Data Augmentation and Contrastive Learning,” arXiv:2401.16349, 2024. https://arxiv.org/abs/2401.16349.','[3] S. B. Zinjad, A. Bhattacharjee, A. Bhilegaonkar and H. Liu, “ResumeFlow: An LLM-facilitated Pipeline for Personalized Resume Generation and Refinement,” arXiv:2402.06221, 2024. https://arxiv.org/abs/2402.06221.','[4] C. Gan, Q. Zhang and T. Mori, “Application of LLM Agents in Recruitment: A Novel Framework for Resume Screening,” arXiv:2401.08315, 2024. https://arxiv.org/abs/2401.08315.','[5] S. Vaishampayan et al., “Human and LLM-Based Resume Matching: An Observational Study,” Findings of NAACL 2025, pp. 4823-4838. https://aclanthology.org/2025.findings-naacl.270/.','[6] H. Nghiem, J. Prindle, J. Zhao and H. Daumé III, “You Gotta be a Doctor, Lin: An Investigation of Name-Based Bias of Large Language Models in Employment Recommendations,” EMNLP 2024, pp. 7268-7287. https://aclanthology.org/2024.emnlp-main.413/.','[7] Project source archive supplied for this report, New folder.zip, Java backend and React frontend source snapshot.','[8] job_platform.sql, supplied MySQL/MariaDB database dump for the job_platform schema.']
for t in refs:
 p=doc.add_paragraph(t);p.paragraph_format.left_indent=Inches(.35);p.paragraph_format.first_line_indent=Inches(-.35);p.paragraph_format.space_after=Pt(8)
doc.core_properties.title='AI Career Assistant Job Discovery and Application Platform';doc.core_properties.subject='Project report based on supplied code and database schema';doc.core_properties.author='Project Team';doc.save(OUT)
print(OUT)
