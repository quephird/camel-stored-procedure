create or replace function greeting
  (name in varchar2)
   return varchar2 as
begin
   return 'Hello, ' || name || '!!!';
end;
/