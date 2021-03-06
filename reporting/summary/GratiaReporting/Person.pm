package GratiaReporting::Person;

use strict;
require Exporter;

use vars qw(@ISA @EXPORT @EXPORT_OK);

@ISA = qw(Exporter);

@EXPORT = qw();
@EXPORT_OK = qw();

1;

sub new {
  my $class = shift;
  my $self = {  };
  if ($_[0]) {
    if (not ref $_[0]) {
      $self->{primary_email} = $_[0] if $_[0];
    } else {
      $self = $_[0];
    }
  }
  bless $self, $class;
  $self->update_derivative_data();
  $self->normalize_case();
  return $self;
}

sub info {
  my $self = shift;
  return sprintf("%s\n%s\n%s\n",
                 '-' x 72,
                 item_info(undef, $self),
                 '-' x 72);
}

sub item_info {
  my $result = "";
  my $indent = shift || '';
  my $tot_items = @_;
  my $n_item = 0;
  foreach my $item (@_) {
    my $ref = ref $item;
    if (not $ref or $ref eq "FUNCTION" or $ref eq "CODE") {
      if ($item =~ m&^[-\d+Ee.]+$&) {
        $result = "$result$item";
      } else {
        $result = "$result\"$item\"";
      }
    } elsif ($ref eq "SCALAR") {
      $result = "$result->$$item";
    } elsif ($ref eq "ARRAY") {
      $result = sprintf("$result\[ %s \]", item_info($indent, @$item));
    } else { # Assume item is a class
      my $tot_keys = scalar keys %$item;
      my $n_key = 0;
      foreach my $key (keys %$item) {
        $result = sprintf("${result}%s%s => %s",
                          $n_key?$indent:'',
                          $key,
                          item_info(' ' x length("$key => "), $item->{$key}));
        ++$n_key;
        if ($n_key < $tot_keys) {
          $result = "$result\n";
        }
      }
    }
    ++$n_item;
    if ($n_item < $tot_items) {
      $result = "$result, ";
    }
  }
  return $result;
}

sub wants_vo_report_oim {
  my ($self, $vo, $reporting_name) = @_;
  $reporting_name = '' unless defined $reporting_name;
  return (grep m&^$vo:$reporting_name&i, @{$self->{vo_reporting_names}})?1:0;
}

sub wants_site_oim {
  my ($self, $site_hash, $wanted_grid_type_hash, $vo) = @_;
  return ($site_hash and
          (not $wanted_grid_type_hash or
           $wanted_grid_type_hash->{$site_hash->{grid_type}}) and
          $vo?(exists $self->{site_vos}->{lc $vo} and
               $self->{site_vos}->{lc $vo} and
               grep "$site_hash->{name}", @{$self->{site_vos}->{lc $vo}}):
          (grep "$site_hash->{name}", @{$self->{sites}})
         )?1:0;
}

sub wants_all_sites {
  my $self = shift;
  return ((exists $self->{all_reports} and $self->{all_reports}->{site}) or
          (exists $self->{sites} and grep m&^all$&i, @{$self->{sites}}))?1:0;
}

sub wants_all_vos {
  my ($self) = @_;
  return ((exists $self->{all_reports} and $self->{all_reports}->{vo}) or
      (exists $self->{vos} and grep m&^all$&i, @{$self->{vos}}))?1:0;
}

sub mergePersonData {
  my ($self, $person_hash) = @_;
    foreach my $key (sort keys %$person_hash) {
      if ($self->{$key}) {
        if (ref $self->{$key} eq 'ARRAY') {
          my %merging_hash = map { $_ => 1} @{$self->{$key}};
          grep { $merging_hash{$_} = 1 } @{$person_hash->{$key}};
          @{$self->{$key}} = sort keys %merging_hash;
        } elsif (ref $self->{$key} eq 'HASH') {
          foreach my $subhash_key (keys %{$person_hash->{$key}}) {
            $self->{$key}->{$subhash_key} =
              $person_hash->{$key}->{$subhash_key} if $person_hash->{$key}->{$subhash_key};
          }
        } else {
          $self->{$key} = $person_hash->{$key} if $person_hash->{$key};
        }
      } else {
        $self->{$key} = $person_hash->{$key};
      }
    }
  $self->update_derivative_data();
  $self->normalize_case();
}

sub normalize_case() {
  my $self = shift;
  foreach my $array_key qw(vos users) {
    $self->{$array_key} = [ map { lc } @{$self->{$array_key}} ]
      if (exists $self->{$array_key} and scalar @{$self->{$array_key}});
  }
  return unless exists $self->{site_vos} and scalar keys %{$self->{site_vos}};
  foreach my $key (keys %{$self->{site_vos}}) {
    next if $key eq lc $key;
    $self->{site_vos}->{lc $key} = $self->{site_vos}->{$key};
    delete $self->{site_vos}->{$key};
  }
}

sub update_derivative_data {
  my $self = shift;
  # Update person's real name.
  my $full_name = sprintf("%s %s %s",
                          $self->{first_name} || "",
                          $self->{middle_name} || "",
                          $self->{last_name} || "");
  $full_name =~ s&^\s*&&;
  $full_name =~ s&\s+& &g;
  $self->{full_name} = $full_name;
  my $contact_string;
  if ($full_name) {
    $contact_string = sprintf("%s <%s>",
                              $full_name,
                              $self->{primary_email});
  } else {
    $contact_string = $self->{primary_email};
  }
  $contact_string =~ s&^\s*&&;
  $self->{contact_string} = $contact_string;
}

__END__


### Local Variables:
### mode: cperl
### End:
