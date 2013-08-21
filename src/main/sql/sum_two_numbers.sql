create or replace procedure sum_two_numbers
  (a in number,
   b in number,
   c out number)
is
begin
  c := a + b;
end;
/